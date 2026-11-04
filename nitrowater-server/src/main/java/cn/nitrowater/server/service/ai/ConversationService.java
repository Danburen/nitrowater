package cn.nitrowater.server.service.ai;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.service.ai.model.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.ChatTurn;
import cn.nitrowater.server.service.ai.model.DocSource;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.infra.enums.MessageRole;
import cn.nitrowater.server.infra.repository.AiConversationRepository;
import cn.nitrowater.server.infra.repository.AiMessageRepository;
import cn.nitrowater.server.infra.specification.ConversationSpec;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Server-side persistence for NitroBot conversations.
 *
 * <p>The browser used to keep the transcript in {@code localStorage} and re-send it on every
 * request. This service moves that state into {@code ai_conversation} / {@code ai_message} so
 * conversations survive devices and can be summarised later. Ownership is always checked by
 * {@code uid}; an unknown uuid and another user's uuid are both reported as {@code 404}.</p>
 *
 * <p>Persistence is deliberately decoupled from the model call: {@link #start} stores the question
 * and returns the history to feed the prompt, while {@link #finish} / {@link #fail} store the
 * outcome once the turn completes (streamed or not).</p>
 */
@Service
public class ConversationService {

    private static final int TITLE_MAX_CHARS = 60;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final TypeReference<List<DocSource>> SOURCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ToolCall>> TOOLS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> SKILLS_TYPE = new TypeReference<>() {
    };

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiProperties properties;
    private final AiModerationService moderation;

    public ConversationService(AiConversationRepository conversations, AiMessageRepository messages,
            AiProperties properties, AiModerationService moderation) {
        this.conversations = conversations;
        this.messages = messages;
        this.properties = properties;
        this.moderation = moderation;
    }

    /**
     * @param uid owner uid
     * @param title optional explicit title; blank derives one from the first question later
     * @return the new, empty conversation
     */
    @Transactional
    public AiConversation create(long uid, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setUuid(UUID.randomUUID().toString());
        conversation.setUid(uid);
        conversation.setTitle(trimToNull(title, 120));
        conversation.setLastMessageAt(Instant.now());
        return conversations.save(conversation);
    }

    /**
     * @param uid owner uid
     * @param cursor {@code lastMessageAt} of the previous page's last item, or {@code null}
     * @param limit requested page size
     * @return conversations ordered by most recent message first
     */
    /**
     * Paged listing scoped to one user.
     *
     * @param uid owner uid
     * @param keyword optional case-insensitive title substring
     * @param status lifecycle filter; {@code null} defaults to {@code ACTIVE}
     * @param page zero-based page index
     * @param size requested page size
     * @return conversations ordered by most recent message first
     */
    @Transactional(readOnly = true)
    public Page<AiConversation> list(long uid, String keyword, ConversationStatus status, int page, int size) {
        ConversationStatus effective = status == null ? ConversationStatus.ACTIVE : status;
        Pageable pageable = PageRequest.of(Math.max(0, page), pageSize(size),
                Sort.by(Sort.Direction.DESC, "lastMessageAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Specification<AiConversation> spec = ConversationSpec.of(uid, effective, keyword);
        return conversations.findAll(spec, pageable);
    }

    /**
     * @param uuid external conversation id
     * @param uid owner uid
     * @return the conversation
     * @throws ResponseStatusException 404 when it does not exist or belongs to someone else
     */
    @Transactional(readOnly = true)
    public AiConversation require(String uuid, long uid) {
        if (StringUtil.isBlank(uuid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationUuid is required");
        }
        return conversations.findByUuidAndUid(uuid, uid)
                .filter(conversation -> conversation.getStatus() != ConversationStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    /**
     * @param uuid external conversation id
     * @param uid owner uid
     * @param before return messages with {@code seq} lower than this value; {@code null} for the newest page
     * @param limit requested page size
     * @return a page of messages in ascending {@code seq} order
     */
    @Transactional(readOnly = true)
    public MessagePage messages(String uuid, long uid, Integer before, Integer limit) {
        AiConversation conversation = require(uuid, uid);
        int size = pageSize(limit);
        int upperBound = before == null ? Integer.MAX_VALUE : before;
        List<AiMessage> descending = messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(
                conversation.getId(), upperBound, Limit.of(size + 1));
        boolean hasMore = descending.size() > size;
        List<AiMessage> page = hasMore ? descending.subList(0, size) : descending;
        List<AiMessage> ascending = new ArrayList<>(page);
        ascending.sort((left, right) -> Integer.compare(left.getSeq(), right.getSeq()));
        return new MessagePage(List.copyOf(ascending), hasMore);
    }

    /**
     * @param uuid external conversation id
     * @param uid owner uid
     * @param title new title, or {@code null} to keep it
     * @param archived archive flag, or {@code null} to keep the current state
     * @return the updated conversation
     */
    @Transactional
    public AiConversation update(String uuid, long uid, String title, Boolean archived) {
        AiConversation conversation = require(uuid, uid);
        String trimmed = trimToNull(title, 120);
        if (trimmed != null) {
            conversation.setTitle(trimmed);
        }
        if (archived != null) {
            conversation.setStatus(archived ? ConversationStatus.ARCHIVED : ConversationStatus.ACTIVE);
        }
        conversation.setUpdatedAt(Instant.now());
        return conversations.save(conversation);
    }

    /**
     * Soft-deletes the conversation; messages are kept until a later purge.
     *
     * @param uuid external conversation id
     * @param uid owner uid
     */
    @Transactional
    public void delete(String uuid, long uid) {
        AiConversation conversation = require(uuid, uid);
        conversation.setStatus(ConversationStatus.DELETED);
        conversation.setUpdatedAt(Instant.now());
        conversations.save(conversation);
    }

    /**
     * Stores the user's question and returns the history the prompt should use.
     *
     * <p>When {@code conversationUuid} is blank a new conversation is created, so a client can stay
     * stateless and simply send the uuid it gets back. History is read before the new message is
     * appended, keeping the current question out of the history.</p>
     *
     * @param uid owner uid
     * @param conversationUuid existing conversation, or {@code null} to start a new one
     * @param question the sanitized question
     * @return the conversation plus the history to feed the model
     */
    @Transactional
    public StartedTurn start(long uid, String conversationUuid, String question) {
        // The content gate runs before anything is written: a rejected question must not create a
        // conversation or an ai_message row that later has to be scrubbed by hand.
        moderation.guardQuestion(uid, conversationUuid, question);
        AiConversation conversation = (StringUtil.isBlank(conversationUuid))
                ? create(uid, deriveTitle(question))
                : require(conversationUuid, uid);
        List<ChatTurn> history = history(conversation);
        append(conversation, MessageRole.USER, question, 0, null, null, null, null, false);
        return new StartedTurn(conversation, history);
    }

    /**
     * Stores the assistant answer and updates the conversation aggregates.
     *
     * <p>A continuation turn ("continue generating") extends the truncated answer that is already
     * stored instead of appending a second assistant row: both halves are one bubble in the UI, so
     * persisting two rows would split it on reload and drop the earlier citations/tools/skills.</p>
     *
     * @param conversation the conversation returned by {@link #start}
     * @param answer final answer text (only the newly generated part on a continuation)
     * @param sources citations, may be empty
     * @param tools tool trace, may be empty
     * @param skills skills loaded by the turn, may be empty
     * @param model model that produced the answer
     * @param usage token usage of this turn
     * @param moderated whether the answer was masked by the content gate, stored with the message so
     *        the client can still show the badge after a reload
     * @param continuing whether this turn continues the stored last assistant message in place
     */
    @Transactional
    public void finish(AiConversation conversation, String answer, List<DocSource> sources,
            List<ToolCall> tools, List<String> skills, String model, AiTokenUsage usage,
            boolean moderated, boolean continuing) {
        String raw = Objects.requireNonNullElse(answer, "");
        // A new answer is trimmed as a whole; a continuation is appended verbatim, because the
        // leading space (or lack of one) of the next sentence is part of the text.
        String text = continuing ? raw : raw.trim();
        // `ai_message.tokens` measures the message itself (used for context estimates), so it keeps
        // the completion side only; the daily ledger separately records prompt + completion.
        int tokens = (int) Math.min(Integer.MAX_VALUE, Math.max(0, usage.outputTokens()));
        if (continuing) {
            AiMessage previous = messages
                    .findFirstByConversationIdAndRoleOrderBySeqDesc(conversation.getId(),
                            MessageRole.ASSISTANT)
                    .orElse(null);
            if (previous != null) {
                merge(conversation, previous, text, sources, tools, skills, model, tokens, moderated);
                return;
            }
        }
        append(conversation, MessageRole.ASSISTANT, text, tokens,
                jsonOrNull(sources), jsonOrNull(tools), jsonOrNull(skills),
                trimToNull(model, 64), moderated);
    }

    /**
     * Records a failed turn so the user still sees it after a reload.
     *
     * @param conversation the conversation returned by {@link #start}
     * @param errorMessage human-readable failure reason
     */
    @Transactional
    public void fail(AiConversation conversation, String errorMessage) {
        String text = Objects.requireNonNullElse(errorMessage, "AI request failed").trim();
        append(conversation, MessageRole.ERROR, text, 0, null, null, null, null, false);
    }

    /**
     * Loads an existing conversation for a continuation turn without appending a user message.
     *
     * <p>{@code /clear} aside, this is what the "continue generating" button uses: the truncated
     * answer is already stored, so the prompt only needs the history plus a "keep going"
     * instruction that is never persisted.</p>
     *
     * @param uuid external conversation id
     * @param uid owner uid
     * @return the conversation plus the history to feed the model
     */
    @Transactional(readOnly = true)
    public StartedTurn resume(String uuid, long uid) {
        AiConversation conversation = require(uuid, uid);
        return new StartedTurn(conversation, history(conversation));
    }

    /**
     * Reads the turns that still belong to the model's context window.
     *
     * <p>{@code summary_upto_seq} is the context boundary: messages at or below it were either
     * folded into {@code summary} or dropped by {@code /clear}. Filtering on it is what prevents
     * the old messages from being fed a second time alongside the summary.</p>
     *
     * @param conversation conversation to read
     * @return the summary (when present) followed by at most {@code maxHistoryTurns} turns, oldest first
     */
    @Transactional(readOnly = true)
    public List<ChatTurn> history(AiConversation conversation) {
        int maxEntries = Math.max(0, properties.maxHistoryTurns()) * 2;
        if (maxEntries == 0) {
            return List.of();
        }
        int boundary = Math.max(0, conversation.getSummaryUptoSeq());
        List<AiMessage> descending = messages.findByConversationIdAndSeqGreaterThanOrderBySeqDesc(
                conversation.getId(), boundary, Limit.of(maxEntries));
        List<ChatTurn> history = new ArrayList<>(descending.size() + 1);
        // Earlier turns may already be folded into a rolling summary (T12) or dropped by /clear; the
        // summary, when present, is fed as a synthetic turn so the model keeps continuity without
        // re-reading (and double-charging for) messages at or below the boundary.
        String summary = conversation.getSummary();
        if (StringUtil.isNotBlank(summary)) {
            history.add(new ChatTurn("assistant", "【此前对话摘要】" + summary.trim()));
        }
        for (int index = descending.size() - 1; index >= 0; index--) {
            AiMessage message = descending.get(index);
            String role =  switch (message.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case ERROR -> null;
            };

            if (role != null && StringUtil.isNotBlank(message.getContent())) {
                history.add(new ChatTurn(role, message.getContent()));
            }
        }
        return List.copyOf(history);
    }

    /**
     * Drops every stored turn from the model's context without deleting anything.
     *
     * <p>This backs the {@code /clear} slash command: the transcript stays intact (and readable
     * after a reload), but the next prompt only sees messages appended after this call. The
     * boundary uses the same {@code summary_upto_seq} column as rolling compression, so a later
     * summary starts from a clean slate instead of re-summarising already-dropped turns.</p>
     *
     * @param uuid external conversation id
     * @param uid owner uid
     * @return how many messages left the context and where the new boundary sits
     */
    @Transactional
    public ContextReset resetContext(String uuid, long uid) {
        AiConversation conversation = require(uuid, uid);
        int previousBoundary = Math.max(0, conversation.getSummaryUptoSeq());
        int boundary = messages.findFirstByConversationIdOrderBySeqDesc(conversation.getId())
                .map(AiMessage::getSeq)
                .orElse(previousBoundary);
        long dropped = messages.countByConversationIdAndSeqGreaterThan(conversation.getId(), previousBoundary);
        conversation.setSummary(null);
        conversation.setSummaryUptoSeq(Math.max(boundary, previousBoundary));
        conversation.setTokensEstimate(0);
        conversation.setUpdatedAt(Instant.now());
        conversations.save(conversation);
        return new ContextReset(true, (int) Math.min(Integer.MAX_VALUE, dropped),
                Math.max(boundary, previousBoundary));
    }

    /**
     * @param uid owner uid
     * @return number of non-deleted conversations (used by quotas later)
     */
    @Transactional(readOnly = true)
    public long countActive(long uid) {
        return conversations.countByUidAndStatusNot(uid, ConversationStatus.DELETED);
    }

    private void append(AiConversation conversation, MessageRole role, String content, int tokens,
            String sourcesJson, String toolsJson, String skillsJson, String model, boolean moderated) {
        AiMessage message = new AiMessage();
        message.setUuid(UUID.randomUUID().toString());
        message.setConversationId(conversation.getId());
        message.setSeq(nextSeq(conversation.getId()));
        message.setRole(role);
        message.setContent(content);
        message.setTokens(tokens);
        message.setSourcesJson(sourcesJson);
        message.setToolsJson(toolsJson);
        message.setSkillsJson(skillsJson);
        message.setModel(model);
        message.setModerated(moderated);
        messages.save(message);

        conversation.setMessageCount(conversation.getMessageCount() + 1);
        conversation.setTokensEstimate(conversation.getTokensEstimate() + tokens);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setUpdatedAt(Instant.now());
        if (StringUtil.isBlank(conversation.getTitle())) {
            conversation.setTitle(deriveTitle(content));
        }
        conversations.save(conversation);
    }

    private int nextSeq(Long conversationId) {
        return messages.findFirstByConversationIdOrderBySeqDesc(conversationId)
                .map(last -> last.getSeq() + 1)
                .orElse(1);
    }

    /**
     * Extends the stored assistant message with the continuation's text and trace.
     *
     * @param conversation owning conversation (aggregates are updated)
     * @param message the assistant row to extend
     * @param text newly generated text
     * @param sources citations of the continuation
     * @param tools tool calls of the continuation
     * @param skills skills loaded by the continuation
     * @param model model that produced the continuation
     * @param tokens completion tokens of the continuation
     * @param moderated whether the continuation was masked
     */
    private void merge(AiConversation conversation, AiMessage message, String text,
            List<DocSource> sources, List<ToolCall> tools, List<String> skills, String model,
            int tokens, boolean moderated) {
        message.setContent(Objects.requireNonNullElse(message.getContent(), "") + text);
        message.setTokens(Objects.requireNonNullElse(message.getTokens(), 0) + tokens);
        message.setSourcesJson(mergeSources(message.getSourcesJson(), sources));
        message.setToolsJson(JsonUtil.mergeJson(message.getToolsJson(), tools, TOOLS_TYPE,
                tool -> tool.name() + '|' + tool.argument() + '|' + tool.status() + '|'
                        + tool.resultCount()));
        message.setSkillsJson(mergeSkills(message.getSkillsJson(), skills));
        if (model != null) {
            message.setModel(model);
        }
        message.setModerated(Boolean.TRUE.equals(message.getModerated()) || moderated);
        messages.save(message);

        // The row count does not change, but the answer grew, so context estimates and the sidebar
        // ordering must follow.
        conversation.setTokensEstimate(conversation.getTokensEstimate() + tokens);
        conversation.setLastMessageAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
        conversations.save(conversation);
    }

    private static String jsonOrNull(List<?> values) {
        return values == null || values.isEmpty() ? null : JsonUtil.toJson(values);
    }

    /**
     * Merges citations, keeping the original numbering (inline {@code [n]} chips of the earlier
     * half must still resolve). A continuation that reuses a taken number is renumbered.
     *
     * @param stored JSON of the citations already persisted
     * @param extra citations of the continuation
     * @return merged JSON, or {@code null} when there is none
     */
    private static String mergeSources(String stored, List<DocSource> extra) {
        List<DocSource> merged = new ArrayList<>(JsonUtil.parseList(stored, SOURCES_TYPE));
        Set<String> seen = new LinkedHashSet<>();
        Set<Integer> used = new LinkedHashSet<>();
        int nextIndex = 1;
        for (DocSource source : merged) {
            seen.add(sourceKey(source));
            used.add(source.index());
            nextIndex = Math.max(nextIndex, source.index() + 1);
        }
        if (extra != null) {
            for (DocSource source : extra) {
                if (!seen.add(sourceKey(source))) {
                    continue;
                }
                int index = used.add(source.index()) ? source.index() : nextIndex++;
                used.add(index);
                merged.add(new DocSource(index, source.kind(), source.title(), source.section(),
                        source.path(), source.url(), source.score(), source.snippet()));
            }
        }
        return merged.isEmpty() ? null : JsonUtil.toJson(merged);
    }

    private static String mergeSkills(String stored, List<String> extra) {
        Set<String> merged = new LinkedHashSet<>(JsonUtil.parseList(stored, SKILLS_TYPE));
        if (extra != null) {
            merged.addAll(extra);
        }
        return merged.isEmpty() ? null : JsonUtil.toJson(merged);
    }

    private static String sourceKey(DocSource source) {
        return source.index() + '|' + source.kind() + '|' + source.url() + '|' + source.title()
                + '|' + source.section();
    }

    private static String deriveTitle(String text) {
        String flat = Objects.requireNonNullElse(text, "").replaceAll("\\s+", " ").trim();
        if (flat.isEmpty()) {
            return null;
        }
        return flat.length() <= TITLE_MAX_CHARS ? flat : flat.substring(0, TITLE_MAX_CHARS);
    }

    private static String trimToNull(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }

    private static int pageSize(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    /**
     * Result of {@link #start}: the conversation plus the history captured before the question.
     *
     * @param conversation the conversation that owns the persisted question
     * @param history previous turns to feed the model
     */
    public record StartedTurn(AiConversation conversation, List<ChatTurn> history) {
    }

    /**
     * A page of messages in ascending sequence order.
     *
     * @param items messages
     * @param hasMore whether older messages exist
     */
    public record MessagePage(List<AiMessage> items, boolean hasMore) {
    }

    /**
     * Outcome of {@link #resetContext(String, long)}.
     *
     * @param cleared always {@code true} when the call returns
     * @param droppedMessages how many turns were removed from the model's context
     * @param boundarySeq highest seq excluded from the context ({@code 0} for an empty conversation)
     */
    public record ContextReset(boolean cleared, int droppedMessages, int boundarySeq) {
    }
}
