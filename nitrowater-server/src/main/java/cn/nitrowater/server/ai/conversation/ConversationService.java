package cn.nitrowater.server.ai.conversation;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.properties.AiProperties;
import cn.nitrowater.server.ai.AiTokenUsage;
import cn.nitrowater.server.ai.ChatTurn;
import cn.nitrowater.server.ai.DocSource;
import cn.nitrowater.server.ai.agent.ToolCall;
import cn.nitrowater.server.ai.moderation.AiModerationService;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.infra.enums.MessageRole;
import cn.nitrowater.server.infra.repository.AiConversationRepository;
import cn.nitrowater.server.infra.repository.AiMessageRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    @Transactional(readOnly = true)
    public List<AiConversation> list(long uid, Instant cursor, Integer limit) {
        Limit page = Limit.of(pageSize(limit));
        if (cursor == null) {
            return conversations.findByUidAndStatusOrderByLastMessageAtDesc(uid, ConversationStatus.ACTIVE, page);
        }
        return conversations.findByUidAndStatusAndLastMessageAtLessThanOrderByLastMessageAtDesc(
                uid, ConversationStatus.ACTIVE, cursor, page);
    }

    /**
     * @param uuid external conversation id
     * @param uid owner uid
     * @return the conversation
     * @throws ResponseStatusException 404 when it does not exist or belongs to someone else
     */
    @Transactional(readOnly = true)
    public AiConversation require(String uuid, long uid) {
        if (uuid == null || uuid.isBlank()) {
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
        AiConversation conversation = (conversationUuid == null || conversationUuid.isBlank())
                ? create(uid, deriveTitle(question))
                : require(conversationUuid, uid);
        List<ChatTurn> history = history(conversation);
        append(conversation, MessageRole.USER, question, 0, null, null, null, false);
        return new StartedTurn(conversation, history);
    }

    /**
     * Stores the assistant answer and updates the conversation aggregates.
     *
     * @param conversation the conversation returned by {@link #start}
     * @param answer final answer text
     * @param sources citations, may be empty
     * @param tools tool trace, may be empty
     * @param model model that produced the answer
     * @param moderated whether the answer was masked by the content gate, stored with the message so
     *        the client can still show the badge after a reload
     */
    @Transactional
    public void finish(AiConversation conversation, String answer, List<DocSource> sources,
            List<ToolCall> tools, String model, AiTokenUsage usage, boolean moderated) {
        String text = Objects.requireNonNullElse(answer, "").trim();
        // `ai_message.tokens` measures the message itself (used for context estimates), so it keeps
        // the completion side only; the daily ledger separately records prompt + completion.
        int tokens = (int) Math.min(Integer.MAX_VALUE, Math.max(0, usage.outputTokens()));
        append(conversation, MessageRole.ASSISTANT, text, tokens,
                sources == null || sources.isEmpty() ? null : JsonUtil.toJson(sources),
                tools == null || tools.isEmpty() ? null : JsonUtil.toJson(tools),
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
        append(conversation, MessageRole.ERROR, text, 0, null, null, null, false);
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
        if (summary != null && !summary.isBlank()) {
            history.add(new ChatTurn("assistant", "【此前对话摘要】" + summary.trim()));
        }
        for (int index = descending.size() - 1; index >= 0; index--) {
            AiMessage message = descending.get(index);
            String role = toPromptRole(message.getRole());
            if (role != null && message.getContent() != null && !message.getContent().isBlank()) {
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
            String sourcesJson, String toolsJson, String model, boolean moderated) {
        AiMessage message = new AiMessage();
        message.setUuid(UUID.randomUUID().toString());
        message.setConversationId(conversation.getId());
        message.setSeq(nextSeq(conversation.getId()));
        message.setRole(role);
        message.setContent(content);
        message.setTokens(tokens);
        message.setSourcesJson(sourcesJson);
        message.setToolsJson(toolsJson);
        message.setModel(model);
        message.setModerated(moderated);
        messages.save(message);

        conversation.setMessageCount(conversation.getMessageCount() + 1);
        conversation.setTokensEstimate(conversation.getTokensEstimate() + tokens);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setUpdatedAt(Instant.now());
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(deriveTitle(content));
        }
        conversations.save(conversation);
    }

    private int nextSeq(Long conversationId) {
        return messages.findFirstByConversationIdOrderBySeqDesc(conversationId)
                .map(last -> last.getSeq() + 1)
                .orElse(1);
    }

    private static String toPromptRole(MessageRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case ERROR -> null;
        };
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
