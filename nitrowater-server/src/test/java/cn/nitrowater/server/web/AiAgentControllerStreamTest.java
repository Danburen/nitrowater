package cn.nitrowater.server.web;

import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.service.ai.AgentTurnService;
import cn.nitrowater.server.service.ai.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.AiModerationService;
import cn.nitrowater.server.service.ai.ConversationService;
import cn.nitrowater.server.service.ai.SkillRegistry;
import cn.nitrowater.server.service.ai.model.ConversationTurn;
import cn.nitrowater.server.service.ai.model.agent.AiAgentService;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.web.dto.AiAgentReq;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies that opening the agent SSE stream never waits for context assembly. */
class AiAgentControllerStreamTest {

    private final AiAgentService agentService = mock(AiAgentService.class);
    private final ConversationService conversations = mock(ConversationService.class);
    private final SkillRegistry skills = mock(SkillRegistry.class);
    private final AiProperties properties = mock(AiProperties.class);
    private final AiAdminSettingsService adminSettings = mock(AiAdminSettingsService.class);
    private final AiModerationService moderation = mock(AiModerationService.class);
    private final AgentTurnService turns = new AgentTurnService(agentService, conversations, moderation,
            skills, adminSettings, properties, "test-model", false);
    private final AiAgentController controller = new AiAgentController(turns);

    @Test
    void streamSchedulesContextAssemblyOffTheRequestThread() throws Exception {
        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getUuid()).thenReturn("c-1");
        when(conversations.start(eq(7L), isNull(), eq("hi")))
                .thenReturn(new ConversationTurn(conversation, List.of()));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> prepareThread = new AtomicReference<>();
        when(agentService.prepare(anyString(), anyList(), any(), anyBoolean(), eq(7L), eq("c-1")))
                .thenAnswer(invocation -> {
                    prepareThread.set(Thread.currentThread());
                    entered.countDown();
                    release.await(3, TimeUnit.SECONDS);
                    throw new IllegalStateException("stop after observing the thread");
                });

        SseEmitter emitter = controller.stream(
                new AiAgentReq("hi", null, List.of(), null, false, false),
                (Principal) () -> "7");

        try {
            // The handler returned while prepare() was still blocked, which is what lets Spring MVC
            // initialize the async response and flush conversation/start/thinking immediately.
            assertThat(emitter).isNotNull();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(prepareThread.get()).isNotSameAs(Thread.currentThread());
        } finally {
            release.countDown();
        }
    }
}
