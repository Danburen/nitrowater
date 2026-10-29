package cn.nitrowater.server.ai.conversation;

import cn.nitrowater.server.ai.AiProperties;
import cn.nitrowater.server.ai.ChatTurn;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.infra.enums.MessageRole;
import cn.nitrowater.server.infra.repository.AiConversationRepository;
import cn.nitrowater.server.infra.repository.AiMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the context boundary shared by rolling summaries and the {@code /clear} command. */
class ConversationServiceTest {

    private final AiConversationRepository conversations = mock(AiConversationRepository.class);
    private final AiMessageRepository messages = mock(AiMessageRepository.class);
    private final AiProperties properties = mock(AiProperties.class);
    private final ConversationService service =
            new ConversationService(conversations, messages, properties);

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
        assertThat(history.get(0).content()).isEqualTo("【此前对话摘要】earlier turns");
        assertThat(history.get(1).content()).isEqualTo("after boundary");
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

        ConversationService.ContextReset reset = service.resetContext("c-1", 7L);

        assertThat(reset.cleared()).isTrue();
        assertThat(reset.boundarySeq()).isEqualTo(9);
        assertThat(reset.droppedMessages()).isEqualTo(7);
        verify(conversation).setSummary(null);
        verify(conversation).setSummaryUptoSeq(9);
        verify(conversation).setTokensEstimate(0);
        verify(conversations).save(conversation);
    }
}
