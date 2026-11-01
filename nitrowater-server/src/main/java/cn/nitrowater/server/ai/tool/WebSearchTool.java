package cn.nitrowater.server.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.agent.AgentDoc;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.properties.WebSearchProperties;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
 * {@code remaining} so the model can decide "enough, answer now" instead of searching again.
 * The UI still gets a short {@code snippet}, but the model payload omits it because it duplicates
 * the prefix of {@code content}.</p>
 */
@Component
public class WebSearchTool {

    private static final int MAX_SNIPPET_CHARS = 240;
    private static final int MAX_CONTENT_CHARS = 1200;
    private static final int MAX_QUERY_CHARS = 200;
    private static final String ENOUGH_HINT = "若这些结果已覆盖回答所需的关键事实，请直接作答，不要再搜索。";

    private final WebSearchProvider provider;
    private final WebSearchProperties properties;

    public WebSearchTool(WebSearchProvider provider, WebSearchProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * @param query search query
     * @param context Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON results, or an error message the model can explain to the user
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
            if (trace != null) {
                trace.recordTool("webSearch", sanitized, 0);
            }
            return JsonUtil.toJson(Map.of("error", "invalid query"));
        }
        if (!provider.enabled()) {
            if (trace != null) {
                trace.recordTool("webSearch", sanitized, 0, "disabled", "server has no search key");
            }
            return JsonUtil.toJson(Map.of("error", "web search is not enabled on this server"));
        }
        int maxSuccesses = properties.effectiveMaxSuccesses();
        int remaining = trace == null ? maxSuccesses : trace.remainingWebSearches(maxSuccesses);
        if (trace != null && trace.hasSearched(sanitized)) {
            trace.recordTool("webSearch", sanitized, 0, "duplicate", "该查询本轮已搜索过");
            return JsonUtil.toJson(Map.of(
                    "error", "duplicate query",
                    "message", "该查询本轮已经搜索过，请直接使用已有结果回答，不要重复搜索。",
                    "remaining", remaining));
        }
        if (trace != null && remaining <= 0) {
            trace.recordTool("webSearch", sanitized, 0, "limit_reached", "本轮联网次数已用完");
            return JsonUtil.toJson(Map.of(
                    "error", "web search limit reached for this turn",
                    "message", "本轮联网搜索次数已用完，请基于已有信息回答并说明局限。",
                    "remaining", 0));
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
            if (trace != null) {
                trace.recordTool("webSearch", sanitized, payload.size());
            }
            if (payload.isEmpty()) {
                return JsonUtil.toJson(Map.of(
                        "results", List.of(),
                        "count", 0,
                        "remaining", left,
                        "message", "没有找到相关网页结果，可以换一个更具体的查询，或基于已有信息回答。"));
            }
            return JsonUtil.toJson(Map.of(
                    "results", payload,
                    "count", payload.size(),
                    "remaining", left,
                    "hint", ENOUGH_HINT));
        } catch (Exception ex) {
            int left = trace == null ? 0 : trace.remainingWebSearches(maxSuccesses);
            if (trace != null) {
                trace.recordTool("webSearch", sanitized, 0, "failed", message(ex));
            }
            return JsonUtil.toJson(Map.of(
                    "error", "web search failed: " + message(ex),
                    "retryable", true,
                    "remaining", left,
                    "message", "联网检索失败（已自动重试）。可以换一个更具体的查询再试一次；若仍失败，请说明无法联网核实。"));
        }
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

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }
}
