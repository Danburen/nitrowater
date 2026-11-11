package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import cn.nitrowater.server.config.SandboxProperties;
import cn.nitrowater.server.infra.exception.SandboxException;
import cn.nitrowater.server.infra.ratelimit.SandboxGate;
import cn.nitrowater.server.service.ai.tool.RunCodeTool;
import cn.nitrowater.server.service.sandbox.SandboxExecutionGuard;
import cn.nitrowater.server.service.sandbox.model.RunCommand;
import cn.nitrowater.server.service.sandbox.model.RunResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the agent-facing contract of {@code runCode}: queue probing, attempt budget, status
 * mapping and trace entries.
 */
class RunCodeToolTest {

    private static final long WAIT_MS = 15_000L;

    @Test
    void runsCodeAndRecordsAnOkToolCall() {
        SandboxExecutionGuard guard = mock(SandboxExecutionGuard.class);
        when(guard.snapshot()).thenReturn(new SandboxGate.Snapshot(0, 1, 20, WAIT_MS));
        when(guard.execute(eq("u1"), any(RunCommand.class), eq(WAIT_MS)))
                .thenReturn(new RunResult("hello", "", "OK", 0, 5L, 1024L, null));
        AgentTrace trace = new AgentTrace("u1");

        String json = new RunCodeTool(guard, properties())
                .runCode("java", "class Main {}", null, context(trace));

        assertThat(json).contains("\"code\":\"tool.ok\"", "\"stdout\":\"hello\"", "\"attempt\":1");
        assertThat(trace.calls()).containsExactly(
                new ToolCall("runCode", "java · class Main {}", 1, "tool.ok", "运行成功 · 5ms"));
    }

    @Test
    void asksTheUserWhenTheQueueIsLongAndDoesNotConsumeAnAttempt() {
        SandboxExecutionGuard guard = mock(SandboxExecutionGuard.class);
        when(guard.snapshot()).thenReturn(new SandboxGate.Snapshot(3, 1, 20, WAIT_MS));
        AgentTrace trace = new AgentTrace("u1");

        String json = new RunCodeTool(guard, properties())
                .runCode("java", "class Main {}", null, context(trace));

        assertThat(json).contains("\"code\":\"tool.queued\"", "\"waiting\":3");
        assertThat(trace.calls()).extracting(ToolCall::status).containsExactly("tool.queued");
        assertThat(trace.runCodeAttempts()).isZero();
        verify(guard, never()).execute(anyString(), any(), anyLong());
    }

    @Test
    void rejectsWhenTheQueueIsFull() {
        SandboxExecutionGuard guard = mock(SandboxExecutionGuard.class);
        when(guard.snapshot()).thenReturn(new SandboxGate.Snapshot(20, 0, 20, WAIT_MS));

        String json = new RunCodeTool(guard, properties())
                .runCode("java", "class Main {}", null, context(new AgentTrace("u1")));

        assertThat(json).contains("\"code\":\"tool.queue_full\"");
        verify(guard, never()).execute(anyString(), any(), anyLong());
    }

    @Test
    void mapsCompileErrorsAndStopsAfterThreeAttempts() {
        SandboxExecutionGuard guard = mock(SandboxExecutionGuard.class);
        when(guard.snapshot()).thenReturn(new SandboxGate.Snapshot(0, 1, 20, WAIT_MS));
        when(guard.execute(eq("u1"), any(RunCommand.class), eq(WAIT_MS)))
                .thenReturn(new RunResult(null, "cannot find symbol", "CE", 1, 4L, 1024L,
                        "compilation failed"));
        AgentTrace trace = new AgentTrace("u1");
        RunCodeTool tool = new RunCodeTool(guard, properties());

        String first = tool.runCode("java", "class Main { broken }", null, context(trace));
        tool.runCode("java", "class Main { broken }", null, context(trace));
        tool.runCode("java", "class Main { broken }", null, context(trace));
        String fourth = tool.runCode("java", "class Main { broken }", null, context(trace));

        assertThat(first).contains("\"code\":\"tool.compile_error\"", "cannot find symbol");
        assertThat(fourth).contains("\"code\":\"tool.retry_exhausted\"", "\"attempt\":4");
        assertThat(trace.calls()).extracting(ToolCall::status)
                .containsExactly("tool.compile_error", "tool.compile_error", "tool.compile_error",
                        "tool.retry_exhausted");
        assertThat(trace.runCodeAttempts()).isEqualTo(4);
        verify(guard, times(3)).execute(eq("u1"), any(RunCommand.class), eq(WAIT_MS));
    }

    @Test
    void mapsQuotaAndServerFailuresWithoutLeakingInternals() {
        SandboxExecutionGuard guard = mock(SandboxExecutionGuard.class);
        when(guard.snapshot()).thenReturn(new SandboxGate.Snapshot(0, 1, 20, WAIT_MS));
        when(guard.execute(eq("u1"), any(RunCommand.class), eq(WAIT_MS)))
                .thenThrow(new SandboxExecutionGuard.GuardException(
                        HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "internal quota detail"));
        AgentTrace trace = new AgentTrace("u1");
        RunCodeTool tool = new RunCodeTool(guard, properties());

        String limited = tool.runCode("java", "class Main {}", null, context(trace));
        assertThat(limited).contains("\"code\":\"tool.rate_limited\"")
                .doesNotContain("internal quota detail");

        when(guard.execute(eq("u1"), any(RunCommand.class), eq(WAIT_MS)))
                .thenThrow(new SandboxException(HttpStatus.SERVICE_UNAVAILABLE,
                        "sandbox_unavailable", "piston stack trace"));
        String unavailable = tool.runCode("java", "class Main {}", null, context(trace));
        assertThat(unavailable).contains("\"code\":\"tool.unavailable\"")
                .doesNotContain("piston stack trace");
    }

    @Test
    void rejectsAnAnonymousTrace() {
        SandboxExecutionGuard guard = mock(SandboxExecutionGuard.class);

        String json = new RunCodeTool(guard, properties())
                .runCode("java", "class Main {}", null, context(new AgentTrace()));

        assertThat(json).contains("\"code\":\"tool.unauthorized\"");
        verify(guard, never()).snapshot();
    }

    private static ToolContext context(AgentTrace trace) {
        return new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));
    }

    private static SandboxProperties properties() {
        return new SandboxProperties(
                "http://localhost:2000",
                2_000,
                30_000,
                65_536,
                2,
                20_000,
                10,
                200,
                20,
                WAIT_MS,
                10_000,
                65_536,
                268_435_456L,
                WAIT_MS,
                3,
                5_000L,
                3_000L);
    }
}
