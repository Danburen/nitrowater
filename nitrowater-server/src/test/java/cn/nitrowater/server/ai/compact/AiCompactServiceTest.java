package cn.nitrowater.server.ai.compact;

import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.infra.enums.MessageRole;
import cn.nitrowater.server.infra.repository.AiConversationRepository;
import cn.nitrowater.server.infra.repository.AiMessageRepository;
import cn.nitrowater.server.service.ai.conversation.AiCompactService;
import cn.nitrowater.server.service.ai.ops.AiModerationService;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.service.ai.model.usage.AiUsageTracker;
import cn.nitrowater.server.service.ai.model.usage.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.agent.CompactResult;
import cn.nitrowater.server.config.ai.ContextProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rolling-summary compaction: CAS, rejection of unsafe summaries and the auto trigger. */
class AiCompactServiceTest {

    private final AiConversationRepository conversations = mock(AiConversationRepository.class);
    private final AiMessageRepository messages = mock(AiMessageRepository.class);
    private final AiModerationService moderation = mock(AiModerationService.class);
    private final AiUsageService usage = mock(AiUsageService.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final ContextProperties context = new ContextProperties(4000, 60, true, 32, 1, 800, 8000);
    private final AiCompactService service =
            new AiCompactService(conversations, messages, context, moderation, usage, chatModel);

    /** Four stored messages: two are foldable when keep-turns=1. */
    private final List<AiMessage> pending = List.of(
            message(1, MessageRole.USER, "第一个问题"),
            message(2, MessageRole.ASSISTANT, "第一个回答"),
            message(3, MessageRole.USER, "最近的问题"),
            message(4, MessageRole.ASSISTANT, "最近的回答"));

    @Test
    void compactFoldsTheOldestTurnsAndAdvancesTheBoundary() {
        conversation();
        stubPending();
        when(messages.countByConversationIdAndSeqGreaterThan(1L, 2)).thenReturn(2L);
        stubSummary("用户询问了第一个问题，助手给出了回答。");
        when(conversations.updateSummary(eq(1L), eq(0), any(String.class), eq(2), any(Instant.class)))
                .thenReturn(1);

        CompactResult result = service.compact(7L, "c-1");

        assertThat(result.compacted()).isTrue();
        assertThat(result.summarizedMessages()).isEqualTo(2);
        assertThat(result.boundarySeq()).isEqualTo(2);
        assertThat(result.remainingMessages()).isEqualTo(2);
        assertThat(result.reason()).isEqualTo("ok");
        assertThat(result.summaryChars()).isPositive();
    }

    @Test
    void aConcurrentCompactionWinsAndThisOneIsDiscarded() {
        conversation();
        stubPending();
        stubSummary("要点。");
        when(conversations.updateSummary(anyLong(), anyInt(), any(String.class), anyInt(),
                any(Instant.class))).thenReturn(0);

        CompactResult result = service.compact(7L, "c-1");

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("concurrent");
        assertThat(result.summarizedMessages()).isZero();
    }

    @Test
    void anUnsafeSummaryIsDroppedBeforeItIsStored() {
        conversation();
        stubPending();
        // Sensitive content must never be laundered into the context through a summary.
        stubSummary("用户的密码是 hunter2，助手记住了。");

        CompactResult result = service.compact(7L, "c-1");

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("summary_rejected");
        verify(conversations, never()).updateSummary(anyLong(), anyInt(), any(String.class),
                anyInt(), any(Instant.class));
    }

    @Test
    void aFlaggedSummaryIsDroppedByModeration() {
        conversation();
        stubPending();
        stubSummary("一段正常的摘要。");
        when(moderation.guardSummary(eq(7L), eq("c-1"), any(String.class))).thenReturn(true);

        CompactResult result = service.compact(7L, "c-1");

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("summary_rejected");
        verify(conversations, never()).updateSummary(anyLong(), anyInt(), any(String.class),
                anyInt(), any(Instant.class));
    }

    @Test
    void automaticCompactionWaitsForTheTriggerThreshold() {
        conversation();
        when(messages.countByConversationIdAndSeqGreaterThan(1L, 0)).thenReturn(10L);

        CompactResult result = service.autoCompact(7L, "c-1");

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("below_trigger");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private void stubPending() {
        when(messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(eq(1L), eq(0),
                any(Limit.class))).thenReturn(pending);
    }

    private AiConversation conversation() {
        AiConversation conversation = mock(AiConversation.class);
        when(conversations.findByUuidAndUid("c-1", 7L)).thenReturn(Optional.of(conversation));
        when(conversation.getStatus()).thenReturn(ConversationStatus.ACTIVE);
        when(conversation.getId()).thenReturn(1L);
        when(conversation.getUuid()).thenReturn("c-1");
        when(conversation.getSummaryUptoSeq()).thenReturn(0);
        when(conversation.getCompressVersion()).thenReturn(0);
        when(conversation.getSummary()).thenReturn(null);
        return conversation;
    }

    private void stubSummary(String text) {
        AiUsageTracker tracker = new NoopTracker();
        when(usage.start(anyLong(), anyInt())).thenReturn(tracker);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private static AiMessage message(int seq, MessageRole role, String content) {
        AiMessage message = mock(AiMessage.class);
        when(message.getSeq()).thenReturn(seq);
        when(message.getRole()).thenReturn(role);
        when(message.getContent()).thenReturn(content);
        return message;
    }

    /** Minimal accounting double: the real session settles a quota reservation. */
    private static final class NoopTracker implements AiUsageTracker {

        @Override
        public void record(Usage usage) {
        }

        @Override
        public void append(String text) {
        }

        @Override
        public AiTokenUsage finish() {
            return new AiTokenUsage(0, 0, false);
        }
    }
}
