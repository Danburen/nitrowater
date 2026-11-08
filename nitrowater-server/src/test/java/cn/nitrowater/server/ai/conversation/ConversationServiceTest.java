package cn.nitrowater.server.ai.conversation;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.ConversationService;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.service.ai.model.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.ChatTurn;
import cn.nitrowater.server.service.ai.model.ContextReset;
import cn.nitrowater.server.service.ai.model.DocSource;
import cn.nitrowater.server.service.ai.AiModerationService;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.infra.enums.MessageRole;
import cn.nitrowater.server.infra.repository.AiConversationRepository;
import cn.nitrowater.server.infra.repository.AiMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private final ConversationService service =
            new ConversationService(conversations, messages, properties, moderation);

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
}
