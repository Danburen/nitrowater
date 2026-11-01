package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.ai.DocSource;
import cn.nitrowater.server.ai.DocSourceMapper;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-turn, mutable agent state shared with tools through Spring AI's {@code ToolContext}.
 *
 * <p>Tools write into it; the SSE controller reads it when the model finishes. Citation numbers are
 * allocated once per chunk, so a second retrieval of the same section keeps the same {@code [n]}
 * and the UI list stays consistent with the answer text.</p>
 *
 * <p>The authenticated user id travels with the trace because the {@code runCode} tool must apply
 * the same sandbox quotas as the HTTP endpoint, and tool execution may not run on the request
 * thread that owns the security context.</p>
 */
public final class AgentTrace {

    /** Key under which this object is put into the {@code toolContext} map. */
    public static final String CONTEXT_KEY = "nitrowater.agent.trace";

    private final String userId;
    private final String conversationUuid;
    private final Map<String, AgentDoc> docs = new LinkedHashMap<>();
    private final List<ToolCall> calls = new CopyOnWriteArrayList<>();
    private final Set<String> skills = new LinkedHashSet<>();
    private final AtomicInteger runCodeAttempts = new AtomicInteger();
    private final AtomicBoolean webFallbackUsed = new AtomicBoolean();
    private final AtomicInteger webSearchSuccesses = new AtomicInteger();
    private final Set<String> webQueries = new LinkedHashSet<>();
    private int nextIndex = 1;

    /** Creates a trace without a user id; used by tests and non-authenticated tool paths. */
    public AgentTrace() {
        this(null, null);
    }

    /**
     * @param userId the authenticated user id, or {@code null} when unavailable
     */
    public AgentTrace(String userId) {
        this(userId, null);
    }

    /**
     * @param userId the authenticated user id, or {@code null} when unavailable
     * @param conversationUuid conversation that owns this turn, or {@code null} for legacy callers
     */
    public AgentTrace(String userId, String conversationUuid) {
        this.userId = userId;
        this.conversationUuid = conversationUuid;
    }

    /**
     * Adds retrieved chunks, reusing the citation number of an already-seen section.
     *
     * @param documents chunks in relevance order
     * @return the same chunks paired with their stable citation number
     */
    public synchronized List<AgentDoc> addDocuments(List<Document> documents) {
        List<AgentDoc> added = new ArrayList<>(documents.size());
        for (Document document : documents) {
            String key = key(document);
            AgentDoc existing = docs.get(key);
            if (existing == null) {
                int index = nextIndex++;
                existing = new AgentDoc(index, document, DocSourceMapper.from(index, document));
                docs.put(key, existing);
            }
            added.add(existing);
        }
        return List.copyOf(added);
    }

    /**
     * Adds a web result as a citation (no local document).
     *
     * @param title result title
     * @param url result URL
     * @param snippet short preview
     * @return the citation entry (deduplicated by URL)
     */
    public synchronized AgentDoc addWebResult(String title, String url, String snippet) {
        String key = "web:" + url;
        AgentDoc existing = docs.get(key);
        if (existing != null) {
            return existing;
        }
        int index = nextIndex++;
        DocSource source = new DocSource(index, "web",
                Objects.requireNonNullElse(title, "网页结果"), "", "", Objects.requireNonNullElse(url, ""),
                0.0, Objects.requireNonNullElse(snippet, ""));
        AgentDoc doc = new AgentDoc(index, null, source);
        docs.put(key, doc);
        return doc;
    }

    /**
     * @param name tool name
     * @param argument main argument (truncated by the caller)
     * @param resultCount number of results
     */
    public void recordTool(String name, String argument, int resultCount) {
        calls.add(new ToolCall(name, truncate(argument, 160), resultCount));
    }

    /**
     * Records a tool call with a normalised outcome for the UI.
     *
     * @param name tool name
     * @param argument main argument (truncated here)
     * @param resultCount number of results
     * @param status normalised outcome
     * @param summary short detail, or {@code null}
     */
    public void recordTool(String name, String argument, int resultCount, String status, String summary) {
        calls.add(new ToolCall(name, truncate(argument, 160), resultCount, status,
                truncate(summary, 240)));
    }

    /**
     * Claims the single automatic web-search fallback of this turn.
     *
     * @return {@code true} for the first caller, {@code false} afterwards
     */
    public boolean claimWebFallback() {
        return webFallbackUsed.compareAndSet(false, true);
    }

    /**
     * Releases the fallback claim after a failed search, so a later docs miss can still retry
     * instead of silently losing the turn's only automatic search.
     */
    public void releaseWebFallback() {
        webFallbackUsed.set(false);
    }

    /**
     * @param query search query
     * @return whether this exact query already ran successfully in this turn
     */
    public synchronized boolean hasSearched(String query) {
        return webQueries.contains(normalizeQuery(query));
    }

    /**
     * @param maxSuccesses budget for one turn
     * @return how many more successful searches the turn may run (never negative)
     */
    public int remainingWebSearches(int maxSuccesses) {
        return Math.max(0, maxSuccesses - webSearchSuccesses.get());
    }

    /**
     * Records a search that actually reached the provider, keeping the budget and the
     * de-duplication set in sync.
     *
     * @param query the query that was searched
     */
    public synchronized void recordWebSearchSuccess(String query) {
        webSearchSuccesses.incrementAndGet();
        webQueries.add(normalizeQuery(query));
    }

    /** @return successful searches so far this turn */
    public int webSearchSuccesses() {
        return webSearchSuccesses.get();
    }

    /**
     * Normalizes a query for de-duplication: trimmed, lower-cased, whitespace collapsed.
     *
     * @param query raw query, may be {@code null}
     * @return the comparison key, never {@code null}
     */
    public static String normalizeQuery(String query) {
        return Objects.requireNonNullElse(query, "").trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
    /** @return the authenticated user id, or {@code null} when unavailable */
    public String userId() {
        return userId;
    }

    /** @return the conversation uuid that owns this turn, or {@code null} */
    public String conversationUuid() {
        return conversationUuid;
    }

    /**
     * Allocates the next {@code runCode} attempt number for this turn.
     *
     * @return the 1-based attempt number
     */
    public int nextRunCodeAttempt() {
        return runCodeAttempts.incrementAndGet();
    }

    /** @return how many {@code runCode} attempts were allocated this turn */
    public int runCodeAttempts() {
        return runCodeAttempts.get();
    }

    /** @param name loaded skill name */
    public synchronized void addSkill(String name) {
        if (name != null && !name.isBlank()) {
            skills.add(name.trim());
        }
    }

    /** @return citations in assignment order */
    public synchronized List<DocSource> sources() {
        return docs.values().stream().map(AgentDoc::source).toList();
    }

    /** @return tool calls in execution order */
    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }

    /** @return loaded skills in execution order */
    public synchronized List<String> skills() {
        return List.copyOf(skills);
    }

    /** @return whether any evidence was collected */
    public synchronized boolean hasSources() {
        return !docs.isEmpty();
    }

    /**
     * Deduplicate by chunk id, not by section: a long section can be split into several chunks and
     * each one must keep its own citation number (the model sees one result per chunk).
     */
    private static String key(Document document) {
        return document.getId();
    }

    private static String truncate(String value, int max) {
        String text = Objects.requireNonNullElse(value, "");
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
