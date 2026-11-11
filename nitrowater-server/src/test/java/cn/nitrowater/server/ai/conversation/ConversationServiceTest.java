package cn.nitrowater.server.ai.conversation;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.conversation.ConversationService;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.config.ai.ContextProperties;
import cn.nitrowater.server.service.ai.model.usage.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.agent.ChatTurn;
import cn.nitrowater.server.service.ai.model.agent.ContextReset;
import cn.nitrowater.server.service.ai.model.agent.ConversationContext;
import cn.nitrowater.server.service.ai.model.rag.DocSource;
import cn.nitrowater.server.service.ai.ops.AiModerationService;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.infra.enums.MessageRole;
import cn.nitrowater.server.infra.repository.AiConversationRepository;
import cn.nitrowater.server.infra.repository.AiMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the context boundary shared by rolling summaries and the {@code /clear} command. */
class ConversationServiceTest {

    private final AiConversationRepository conversations = mock(AiConversationRepository.class);
    private final AiMessageRepository messages = mock(AiMessageRepository.class);
    private final AiProperties properties = mock(AiProperties.class);
    private final AiModerationService moderation = mock(AiModerationService.class);
    /** Budget disabled: these tests exercise the legacy turn-count rule and the boundary. */
    private final ContextProperties context =
            new ContextProperties(128000, null, 0, 60, true, 32, null, 1, 800, 8000);
    private final ConversationService service =
            new ConversationService(conversations, messages, properties, context, moderation);

    @Test
    void historyReadsOnlyMessagesAfterTheContextBoundary() {
        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getSummaryUptoSeq()).thenReturn(4);
        when(conversation.getSummary()).thenReturn("earlier turns");
        when(properties.maxHistoryTurns()).thenReturn(8);

        AiMessage kept = mock(AiMessage.class);
        when(kept.getSeq()).thenReturn(5);
        when(kept.getRole()).thenReturn(MessageRole.USER);
        when(kept.getContent()).thenReturn("after boundary");
        when(messages.findByConversationIdAndSeqGreaterThanOrderBySeqDesc(eq(1L), eq(4), any(Limit.class)))
                .thenReturn(List.of(kept));

        List<ChatTurn> history = service.history(conversation);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).content())
                .isEqualTo(ConversationService.SUMMARY_PREFIX + "earlier turns");
        assertThat(history.get(1).content()).isEqualTo("after boundary");
    }

    @Test
    void historyStopsAtTheTokenBudgetKeepingTheNewestTurns() {
        ConversationService budgeted = new ConversationService(conversations, messages, properties,
                new ContextProperties(128000, null, 40, 60, true, 32, null, 1, 800, 8000), moderation);
        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getSummaryUptoSeq()).thenReturn(0);
        when(conversation.getSummary()).thenReturn(null);

        // 100 ASCII chars ≈ 25 tokens + 4 overhead = 29 each, so only one fits a 40-token budget.
        String filler = "x".repeat(100);
        AiMessage newest = message(6, MessageRole.ASSISTANT, "newest " + filler);
        AiMessage older = message(5, MessageRole.USER, "older " + filler);
        when(messages.findByConversationIdAndSeqGreaterThanOrderBySeqDesc(eq(1L), eq(0), any(Limit.class)))
                .thenReturn(List.of(newest, older));

        List<ChatTurn> history = budgeted.history(conversation);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).content()).startsWith("newest ");
    }

    @Test
    void contextSummarizesBothSidesOfTheBoundary() {
        AiConversation conversation = mock(AiConversation.class);
        when(conversations.findByUuidAndUid("c-1", 7L)).thenReturn(Optional.of(conversation));
        when(conversation.getStatus()).thenReturn(ConversationStatus.ACTIVE);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getSummaryUptoSeq()).thenReturn(4);
        when(conversation.getSummary()).thenReturn("  earlier turns  ");
        when(messages.countByConversationIdAndSeqLessThanEqual(1L, 4)).thenReturn(4L);
        when(messages.countByConversationIdAndSeqGreaterThan(1L, 4)).thenReturn(3L);

        ConversationContext view = service.context("c-1", 7L);

        assertThat(view.boundarySeq()).isEqualTo(4);
        assertThat(view.summary()).isEqualTo("earlier turns");
        assertThat(view.summarizedMessages()).isEqualTo(4);
        assertThat(view.pendingMessages()).isEqualTo(3);
        assertThat(view.compactTriggerMessages()).isEqualTo(32);
        assertThat(view.keepMessages()).isEqualTo(2);
    }

    private static AiMessage message(int seq, MessageRole role, String content) {
        AiMessage message = mock(AiMessage.class);
        when(message.getSeq()).thenReturn(seq);
        when(message.getRole()).thenReturn(role);
        when(message.getContent()).thenReturn(content);
        return message;
    }

    @Test
    void resetContextMovesTheBoundaryWithoutDeletingMessages() {
        AiConversation conversation = mock(AiConversation.class);
        when(conversations.findByUuidAndUid("c-1", 7L)).thenReturn(Optional.of(conversation));
        when(conversation.getStatus()).thenReturn(ConversationStatus.ACTIVE);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getSummaryUptoSeq()).thenReturn(2);

        AiMessage last = mock(AiMessage.class);
        when(last.getSeq()).thenReturn(9);
        when(messages.findFirstByConversationIdOrderBySeqDesc(1L)).thenReturn(Optional.of(last));
        when(messages.countByConversationIdAndSeqGreaterThan(1L, 2)).thenReturn(7L);

        ContextReset reset = service.resetContext("c-1", 7L);

        assertThat(reset.cleared()).isTrue();
        assertThat(reset.boundarySeq()).isEqualTo(9);
        assertThat(reset.droppedMessages()).isEqualTo(7);
        verify(conversation).setSummary(null);
        verify(conversation).setSummaryUptoSeq(9);
        verify(conversation).setTokensEstimate(0);
        verify(conversations).save(conversation);
    }

    @Test
    void continuationExtendsTheStoredAnswerInsteadOfAddingARow() {
        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getTokensEstimate()).thenReturn(100);

        AiMessage previous = mock(AiMessage.class);
        when(previous.getContent()).thenReturn("part one ");
        when(previous.getTokens()).thenReturn(10);
        when(previous.getSourcesJson()).thenReturn(JsonUtil.toJson(List.of(
                new DocSource(1, "doc", "Title", "Section", "path.md", "/docs/one", 0.9, "one"))));
        when(previous.getSkillsJson()).thenReturn(JsonUtil.toJson(List.of("search")));
        when(previous.getModerated()).thenReturn(false);
        when(messages.findFirstByConversationIdAndRoleOrderBySeqDesc(1L, MessageRole.ASSISTANT))
                .thenReturn(Optional.of(previous));

        service.finish(conversation, "part two", List.of(
                        new DocSource(1, "doc", "Other", "Other", "other.md", "/docs/two", 0.8, "two")),
                List.of(), List.of("search", "docs"), "model-2",
                new AiTokenUsage(5, 7, false), false, true);

        verify(previous).setContent("part one part two");
        verify(previous).setTokens(17);
        ArgumentCaptor<String> sources = ArgumentCaptor.forClass(String.class);
        verify(previous).setSourcesJson(sources.capture());
        // The reused number 1 belongs to the first half, so the continuation's citation is moved up.
        assertThat(sources.getValue()).contains("/docs/one").contains("/docs/two").contains("\"index\":2");
        ArgumentCaptor<String> skills = ArgumentCaptor.forClass(String.class);
        verify(previous).setSkillsJson(skills.capture());
        assertThat(skills.getValue()).contains("search").contains("docs");
        verify(conversation).setTokensEstimate(107);
        // No second assistant row: the append path (which allocates a seq) must not run.
        verify(messages, never()).findFirstByConversationIdOrderBySeqDesc(1L);
    }

    @Test
    void finishStoresSkillsWithANewAnswer() {
        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getMessageCount()).thenReturn(2);
        when(conversation.getTokensEstimate()).thenReturn(40);
        when(messages.findFirstByConversationIdOrderBySeqDesc(1L)).thenReturn(Optional.empty());

        service.finish(conversation, "answer", List.of(), List.of(), List.of("searchDocs"),
                "model", new AiTokenUsage(3, 4, false), false, false);

        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getSkillsJson()).contains("searchDocs");
        assertThat(saved.getValue().getModerated()).isFalse();
    }

    /**
     * The entity the caller holds was loaded before the model ran, so its counters are stale.
     * Writing it back would undo a concurrent turn's bookkeeping, and the {@code seq} it allocates
     * would collide with the other writer's on {@code uk_ai_message_conv_seq}. The append must use
     * the row re-read under the write lock.
     */
    @Test
    void writesOntoTheLockedRowInsteadOfTheStaleHandedInEntity() {
        AiConversation stale = new AiConversation();
        stale.setId(1L);
        stale.setTitle("t");
        stale.setMessageCount(1);

        AiConversation locked = new AiConversation();
        locked.setId(1L);
        locked.setTitle("t");
        locked.setMessageCount(7);

        when(conversations.findByIdForUpdate(1L)).thenReturn(Optional.of(locked));
        when(messages.findFirstByConversationIdOrderBySeqDesc(1L)).thenReturn(Optional.empty());

        service.finish(stale, "回答", List.of(), List.of(), List.of(), "model",
                new AiTokenUsage(5, 7, false), false, false);

        assertThat(locked.getMessageCount()).isEqualTo(8);
        assertThat(stale.getMessageCount())
                .as("the detached instance is never written back")
                .isEqualTo(1);
        verify(conversations).save(locked);
        verify(conversations, never()).save(stale);
    }

    /**
     * Reading the history and appending the question must happen under the same lock, otherwise two
     * tabs can pick the same {@code seq} before either commits.
     */
    @Test
    void startLocksTheConversationRowBeforeReadingHistory() {
        AiConversation locked = new AiConversation();
        locked.setId(1L);
        locked.setUuid("uuid");
        when(conversations.findByUuidAndUid("uuid", 7L)).thenReturn(Optional.of(locked));
        when(conversations.findByIdForUpdate(1L)).thenReturn(Optional.of(locked));
        when(properties.maxHistoryTurns()).thenReturn(0);

        service.start(7L, "uuid", "question");

        InOrder order = inOrder(conversations, messages);
        order.verify(conversations).findByIdForUpdate(1L);
        order.verify(messages).save(any(AiMessage.class));
        order.verify(conversations).save(locked);
    }
}
