package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.memory.AiMemoryService;
import cn.nitrowater.server.infra.enums.MemoryKind;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies that the model cannot choose the owner or forge the source conversation. */
class MemoryToolsTest {

    private final AiMemoryService memories = mock(AiMemoryService.class);
    private final MemoryTools tools = new MemoryTools(memories);

    @Test
    void saveUsesTraceOwnerAndConversation() {
        AgentTrace trace = new AgentTrace("7", "conversation-1");
        AiMemoryService.MemoryView view = new AiMemoryService.MemoryView(
                "m-1", "PREFERENCE", "Use Java 25", (short) 100, Instant.now(), null);
        when(memories.save(7L, "conversation-1", MemoryKind.PREFERENCE, "Use Java 25"))
                .thenReturn(new AiMemoryService.SaveResult("saved", view, null));

        String json = tools.saveMemory("preference", "Use Java 25", context(trace));

        assertThat(json).contains("\"status\":\"saved\"")
                .contains("\"uuid\":\"m-1\"")
                .contains("\"kind\":\"PREFERENCE\"");
        assertThat(trace.calls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("saveMemory");
            assertThat(call.status()).isEqualTo("saved");
        });
    }

    @Test
    void unknownKindIsRejectedWithoutCallingTheService() {
        AgentTrace trace = new AgentTrace("7", "conversation-1");

        String json = tools.saveMemory("NOT_A_KIND", "Use Java 25", context(trace));

        assertThat(json).contains("\"status\":\"rejected\"");
        verify(memories, never()).save(anyLong(), anyString(), any(MemoryKind.class), anyString());
        assertThat(trace.calls()).singleElement()
                .satisfies(call -> assertThat(call.status()).isEqualTo("rejected"));
    }

    @Test
    void missingUserIsRejectedWithoutCallingTheService() {
        String json = tools.saveMemory("FACT", "Use Java 25", context(new AgentTrace()));

        assertThat(json).contains("\"status\":\"rejected\"");
        verify(memories, never()).save(anyLong(), anyString(), any(MemoryKind.class), anyString());
    }

    private static ToolContext context(AgentTrace trace) {
        return new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));
    }
}
