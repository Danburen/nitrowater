package cn.nitrowater.server.service.ai.tool;

import cn.nitrowater.server.service.ai.model.WebSearchResult;
import cn.nitrowater.server.service.ai.model.agent.AgentDoc;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import cn.nitrowater.server.service.ai.model.tool.ToolResponse;
import cn.nitrowater.server.service.ai.properties.WebSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional web-search tool backed by {@link WebSearchProvider} (DeepSeek).
 *
 * <p>Only advertised when the provider is enabled <em>and</em> the user opted in for the turn
 * ({@code allowWeb}). Each hit already carries a model-ready summary, so no page fetching happens
 * here. Results are recorded as {@code kind=web} citations so the UI can distinguish them from
 * official documentation.</p>
 *
 * <p>The turn budget is enforced here: a call counts only after the provider answered, a repeated
 * query is answered from what the model already has, and every response carries
 * {@code data.remaining} so the model can decide "enough, answer now" instead of searching again.
 * The UI still gets a short {@code snippet}, but the model payload omits it because it duplicates
 * the prefix of {@code content}.</p>
 */
@Component
public class WebSearchTool {

    private static final int MAX_SNIPPET_CHARS = 240;
    private static final int MAX_CONTENT_CHARS = 1200;
    private static final int MAX_QUERY_CHARS = 200;
    private static final String ENOUGH_HINT = "若这些结果已覆盖回答所需的关键事实，请直接作答，不要再搜索。";

    private static final Logger LOG = LoggerFactory.getLogger(WebSearchTool.class);

    private final WebSearchProvider provider;
    private final WebSearchProperties properties;

    public WebSearchTool(WebSearchProvider provider, WebSearchProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * @param query search query
     * @param context Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON {@code {success,code,message,data}} where {@code data} holds
     *         {@code results/count/remaining}
     */
    @Tool(name = "webSearch", description = """
            搜索公开网页。仅在需要近期事件、实时数据、可能变更的信息，或用户明确要求“搜索/查一下”时使用。
            一次返回少量带摘要的结果；结果已能支撑回答时立即停止，不要为同一事实重复搜索。
            失败会自动重试一次且不占用本轮次数。引用时给出原始页面或官方文档。
            """)
    public String webSearch(
            @ToolParam(description = "搜索关键词，尽量具体") String query,
            ToolContext context) {
        AgentTrace trace = trace(context);
        String sanitized = Objects.requireNonNullElse(query, "").trim();
        if (sanitized.isEmpty() || sanitized.length() > MAX_QUERY_CHARS) {
            return ToolResponse.of(ToolCode.INVALID_REQUEST,
                            "检索词不能为空，且不超过 " + MAX_QUERY_CHARS + " 个字符。")
                    .record(trace, "webSearch", sanitized, 0)
                    .json();
        }
        if (!provider.enabled()) {
            return ToolResponse.of(ToolCode.DISABLED, "本服务未开启联网搜索，请基于已有信息回答并说明局限。")
                    .record(trace, "webSearch", sanitized, 0)
                    .json();
        }
        int maxSuccesses = properties.effectiveMaxSuccesses();
        int remaining = trace == null ? maxSuccesses : trace.remainingWebSearches(maxSuccesses);
        if (trace != null && trace.hasSearched(sanitized)) {
            return ToolResponse.of(ToolCode.DUPLICATE,
                            "该查询本轮已经搜索过，请直接使用已有结果回答，不要重复搜索。",
                            Map.of("remaining", remaining))
                    .record(trace, "webSearch", sanitized, 0)
                    .json();
        }
        if (trace != null && remaining <= 0) {
            return ToolResponse.of(ToolCode.LIMIT_REACHED,
                            "本轮联网搜索次数已用完，请基于已有信息回答并说明局限。",
                            Map.of("remaining", 0))
                    .record(trace, "webSearch", sanitized, 0)
                    .json();
        }
        try {
            List<WebSearchResult> results = provider.search(sanitized, properties.effectiveMaxResults());
            if (trace != null) {
                trace.recordWebSearchSuccess(sanitized);
            }
            int left = trace == null ? 0 : trace.remainingWebSearches(maxSuccesses);
            List<Map<String, Object>> payload = new ArrayList<>(results.size());
            for (WebSearchResult result : results) {
                String text = Objects.requireNonNullElse(result.snippet(), "");
                String snippet = truncate(text, MAX_SNIPPET_CHARS);
                AgentDoc doc = trace == null ? null
                        : trace.addWebResult(result.title(), result.url(), snippet);
                int index = doc == null ? payload.size() + 1 : doc.index();
                payload.add(Map.of(
                        "index", index,
                        "title", Objects.requireNonNullElse(result.title(), ""),
                        "url", Objects.requireNonNullElse(result.url(), ""),
                        "content", truncate(text, MAX_CONTENT_CHARS)));
            }
            if (payload.isEmpty()) {
                return ToolResponse.ok(searchData(List.of(), 0, left),
                                "没有找到相关网页结果，可以换一个更具体的查询，或基于已有信息回答。")
                        .record(trace, "webSearch", sanitized, 0)
                        .json();
            }
            return ToolResponse.ok(searchData(payload, payload.size(), left), ENOUGH_HINT)
                    .record(trace, "webSearch", sanitized, payload.size())
                    .json();
        } catch (Exception ex) {
            LOG.warn("web search failed", ex);
            int left = trace == null ? 0 : trace.remainingWebSearches(maxSuccesses);
            return ToolResponse.of(ToolCode.UNAVAILABLE,
                            "联网检索失败（已自动重试）。可以换一个更具体的查询再试一次；若仍失败，请说明无法联网核实。",
                            Map.of("remaining", left))
                    .record(trace, "webSearch", sanitized, 0)
                    .json();
        }
    }

    /**
     * @param results hits to hand to the model
     * @param count number of hits
     * @param remaining searches left in this turn
     * @return the {@code data} object shared by every answer of this tool
     */
    private static Map<String, Object> searchData(List<Map<String, Object>> results, int count,
            int remaining) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", results);
        data.put("count", count);
        data.put("remaining", remaining);
        return data;
    }

    private static AgentTrace trace(ToolContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
        return value instanceof AgentTrace agentTrace ? agentTrace : null;
    }

    private static String truncate(String value, int max) {
        String text = Objects.requireNonNullElse(value, "").replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
