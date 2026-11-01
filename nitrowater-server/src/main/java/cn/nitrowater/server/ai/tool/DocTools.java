package cn.nitrowater.server.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.properties.AiProperties;
import cn.nitrowater.server.ai.DocRetriever;
import cn.nitrowater.server.ai.DocSource;
import cn.nitrowater.server.ai.DocSourceMapper;
import cn.nitrowater.server.ai.agent.AgentDoc;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.properties.WebSearchProperties;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The agent's documentation tools.
 *
 * <p>{@code searchDocs} is the agent's only source of local facts. It reuses {@link DocRetriever}
 * (the same corpus filter as the plain RAG path) and records every hit in the request's
 * {@link AgentTrace}, so the SSE response can send citations that match the model's {@code [n]}
 * markers.</p>
 *
 * <p>When the local corpus has no match, the tool can fall back to one web search for the same
 * query (see {@link WebSearchProperties#autoFallback}). That makes "docs miss → search the web"
 * deterministic instead of relying on the model's judgement; the fallback is claimed at most once
 * per turn through {@link AgentTrace#claimWebFallback()}.</p>
 */
@Component
public class DocTools {

    private static final int MAX_CONTENT_CHARS = 1200;
    private static final int MAX_WEB_SNIPPET_CHARS = 600;

    private final DocRetriever retriever;
    private final AiProperties properties;
    private final WebSearchProvider webSearch;
    private final WebSearchProperties webProperties;
    private final AiAdminSettingsService settings;

    public DocTools(DocRetriever retriever, AiProperties properties, WebSearchProvider webSearch,
            WebSearchProperties webProperties, AiAdminSettingsService settings) {
        this.retriever = retriever;
        this.properties = properties;
        this.webSearch = webSearch;
        this.webProperties = webProperties;
        this.settings = settings;
    }

    /**
     * Searches the indexed documentation (VeloChatX and NitroWater); falls back to the web once
     * when nothing matches.
     *
     * @param query natural-language query
     * @param topK optional result count (1-10)
     * @param context Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON {@code {results:[{index,title,section,path,url,score,content}]}}; web fallback
     *         results carry {@code source:"web"} and the payload carries {@code fallback:"web"}
     */
    @Tool(name = "searchDocs", description = """
            检索本站的官方文档（VeloChatX 与 NitroWater），回答产品、配置、命令、权限或架构问题前优先调用。
            返回带编号的文档片段，引用时使用其中的 index。
            若本站没有相关内容，工具会自动返回联网搜索结果（source=web）。
            """)
    public String searchDocs(
            @ToolParam(description = "自然语言检索词，例如 'chat-format 占位符' 或 'BFF token'") String query,
            @ToolParam(description = "返回条数（1-10），不填使用默认值", required = false) Integer topK,
            ToolContext context) {
        AgentTrace trace = trace(context);
        int limit = topK == null ? properties.topK() : Math.min(10, Math.max(1, topK));
        try {
            List<Document> documents = retriever.search(query, limit, properties.similarityThreshold());
            if (documents.isEmpty()) {
                String fallback = webFallback(query, trace);
                if (fallback != null) {
                    return fallback;
                }
            }
            List<AgentDoc> indexed = trace == null ? List.of() : trace.addDocuments(documents);
            if (trace != null) {
                trace.recordTool("searchDocs", query, documents.size());
            }
            return JsonUtil.toJson(toResult(documents, indexed));
        } catch (Exception ex) {
            if (trace != null) {
                trace.recordTool("searchDocs", query, 0);
            }
            return JsonUtil.toJson(Map.of("error", "search unavailable: " + message(ex)));
        }
    }

    /**
     * Runs the single automatic web search allowed per turn.
     *
     * @return the tool payload, or {@code null} when the fallback is disabled, unavailable or
     *         already used (the caller then reports the normal "no local match" result)
     */
    private String webFallback(String query, AgentTrace trace) {
        if (!webProperties.autoFallback() || !webSearch.enabled()
                || !settings.get().webSearchEnabled()) {
            return null;
        }
        if (trace != null) {
            // The fallback shares the turn's success budget with explicit webSearch calls: a
            // repeated query is answered from what the model already has, and an exhausted budget
            // stops searching instead of paying for the same information twice.
            if (trace.hasSearched(query)
                    || trace.remainingWebSearches(webProperties.effectiveMaxSuccesses()) <= 0) {
                return null;
            }
            if (!trace.claimWebFallback()) {
                return null;
            }
        }
        try {
            List<WebSearchResult> hits = webSearch.search(query, webProperties.effectiveMaxResults());
            if (trace != null) {
                trace.recordWebSearchSuccess(query);
                trace.recordTool("searchDocs", query, 0, "fallback_web",
                        "本站文档无匹配，已自动联网搜索（" + hits.size() + " 条）");
            }
            if (hits.isEmpty()) {
                return JsonUtil.toJson(Map.of(
                        "results", List.of(),
                        "count", 0,
                        "fallback", "web",
                        "message", "本站文档与联网搜索都没有找到相关内容。"));
            }
            List<Map<String, Object>> results = new ArrayList<>(hits.size());
            for (WebSearchResult hit : hits) {
                String text = Objects.requireNonNullElse(hit.snippet(), "");
                String snippet = truncate(text, MAX_WEB_SNIPPET_CHARS);
                AgentDoc doc = trace == null ? null
                        : trace.addWebResult(hit.title(), hit.url(), snippet);
                int index = doc == null ? results.size() + 1 : doc.index();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", index);
                item.put("title", Objects.requireNonNullElse(hit.title(), ""));
                item.put("url", Objects.requireNonNullElse(hit.url(), ""));
                // The UI preview is `snippet`; the model only needs `content`, so the duplicated
                // prefix is left out of the tool payload.
                item.put("content", truncate(text, MAX_CONTENT_CHARS));
                item.put("source", "web");
                results.add(item);
            }
            return JsonUtil.toJson(Map.of(
                    "results", results,
                    "count", results.size(),
                    "fallback", "web",
                    "message", "本站文档没有匹配结果，以下是联网搜索结果；请在答案中说明来自外部搜索。"));
        } catch (Exception ex) {
            if (trace != null) {
                // Give the claim back: a failed call must not consume this turn's only automatic
                // search, and it must not count against the success budget either.
                trace.releaseWebFallback();
                trace.recordTool("searchDocs", query, 0, "fallback_failed", message(ex));
            }
            return JsonUtil.toJson(Map.of(
                    "results", List.of(),
                    "count", 0,
                    "fallback", "web_failed",
                    "message", "本站文档没有匹配结果，联网搜索也失败了。"));
        }
    }

    private static Map<String, Object> toResult(List<Document> documents, List<AgentDoc> indexed) {
        List<Map<String, Object>> results = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            AgentDoc agentDoc = indexed.size() == documents.size() ? indexed.get(i) : null;
            int index = agentDoc == null ? i + 1 : agentDoc.index();
            DocSource source = agentDoc == null ? DocSourceMapper.from(index, document) : agentDoc.source();
            results.add(Map.of(
                    "index", index,
                    "title", source.title(),
                    "section", source.section(),
                    "path", source.path(),
                    "url", source.url(),
                    "score", round(source.score()),
                    "content", truncate(document.getText(), MAX_CONTENT_CHARS)));
        }
        if (results.isEmpty()) {
            return Map.of("results", List.of(), "count", 0,
                    "message", "没有匹配的文档片段；请换一种检索词，或确认文档未覆盖。");
        }
        return Map.of("results", results, "count", results.size());
    }

    private static AgentTrace trace(ToolContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
        return value instanceof AgentTrace agentTrace ? agentTrace : null;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String truncate(String value, int max) {
        String text = Objects.requireNonNullElse(value, "").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }
}
