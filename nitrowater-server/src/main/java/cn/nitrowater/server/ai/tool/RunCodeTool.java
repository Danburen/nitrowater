package cn.nitrowater.server.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.exception.SandboxException;
import cn.nitrowater.server.infra.ratelimit.SandboxGate;
import cn.nitrowater.server.service.sandbox.SandboxExecutionGuard;
import cn.nitrowater.server.web.dto.RunRequest;
import cn.nitrowater.server.web.dto.RunResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The agent's code-execution tool.
 *
 * <p>It deliberately does not retry on its own: a compile/runtime error is returned to the model,
 * which can fix the source and call again. The attempt budget is tracked per turn in
 * {@link AgentTrace}. All executions go through {@link SandboxExecutionGuard}, so agent runs share
 * the HTTP endpoint's per-minute/day quotas and global queue.</p>
 */
@Component
public class RunCodeTool {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ARGUMENT_CHARS = 120;

    private final SandboxExecutionGuard guard;
    private final SandboxProperties properties;

    public RunCodeTool(SandboxExecutionGuard guard, SandboxProperties properties) {
        this.guard = guard;
        this.properties = properties;
    }

    /**
     * Compiles and runs a snippet in the server-side sandbox.
     *
     * @param language one of {@code java}, {@code c}, {@code cpp}, {@code python},
     *                 {@code javascript}, {@code typescript}, {@code lua}
     * @param code complete runnable source without Markdown fences
     * @param stdin optional standard input
     * @param context Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON with a normalised status, stdout/stderr and timing information
     */
    @Tool(name = "runCode", description = """
            在服务端沙箱中编译并运行代码，返回 stdout、stderr、退出码和耗时。
            仅在用户明确要求“运行 / 跑一下 / 测试 / 验证”代码，或你必须实际执行才能确认结果时调用。
            支持 java、c、cpp、python、javascript、typescript、lua。
            编译或运行失败时，根据 stderr 修正代码后再试；本次对话最多运行 3 次（含首次），超出后必须停止，不要重复提交完全相同的代码。
            如果返回 status=queued，说明队列较长，先询问用户是否继续等待，不要直接再次调用。
            """)
    public String runCode(
            @ToolParam(description = "语言：java / c / cpp / python / javascript / typescript / lua")
            String language,
            @ToolParam(description = "完整可运行源码；不要包含 Markdown 代码围栏")
            String code,
            @ToolParam(description = "标准输入，可选", required = false)
            String stdin,
            ToolContext context) {
        AgentTrace trace = trace(context);
        String uid = trace == null ? null : trace.userId();
        if (uid == null || uid.isBlank()) {
            record(trace, language, code, "unauthorized", "需要登录后才能运行代码");
            return errorJson("unauthorized", "需要登录后才能运行代码。", null);
        }

        QueueDecision queue = queueDecision();
        if (queue != null) {
            record(trace, language, code, queue.status(), queue.message());
            return queue.toJson();
        }

        int attempt = trace == null ? 1 : trace.nextRunCodeAttempt();
        if (attempt > MAX_ATTEMPTS) {
            String message = "本次对话中 runCode 已尝试 " + MAX_ATTEMPTS
                    + " 次，仍未成功。请停止重试，向用户说明失败原因并给出修复建议。";
            record(trace, language, code, "retry_exhausted", message);
            return errorJson("retry_exhausted", message, attempt);
        }

        try {
            RunResponse response = guard.execute(uid, new RunRequest(language, code, stdin),
                    properties.agentQueueWaitMs());
            String status = statusOf(response);
            record(trace, language, code, status, summary(status, response));
            return resultJson(status, response, attempt);
        } catch (SandboxExecutionGuard.GuardException ex) {
            String status = guardStatus(ex.code());
            String message = guardMessage(status);
            record(trace, language, code, status, message);
            return errorJson(status, message, attempt);
        } catch (SandboxException ex) {
            String status = sandboxStatus(ex.code());
            String message = sandboxMessage(status);
            record(trace, language, code, status, message);
            return errorJson(status, message, attempt);
        } catch (Exception ex) {
            String message = "代码运行服务器暂时不可用，请稍后再试。";
            record(trace, language, code, "server_unavailable", message);
            return errorJson("server_unavailable", message, attempt);
        }
    }

    /**
     * Asks the user before blocking when the queue is already long or full.
     *
     * @return a queue decision, or {@code null} when the run may proceed
     */
    private QueueDecision queueDecision() {
        SandboxGate.Snapshot snapshot = guard.snapshot();
        if (snapshot.maxQueue() > 0 && snapshot.waiting() >= snapshot.maxQueue()) {
            return new QueueDecision("queue_full", "沙箱队列已满，请稍后再试。", snapshot, 0);
        }
        long estimatedWaitMs = estimatedWaitMs(snapshot);
        boolean tooManyWaiting = snapshot.waiting() >= properties.agentQueueAskWaiting();
        boolean tooLong = estimatedWaitMs >= properties.agentQueueAskMs();
        if (tooManyWaiting || tooLong) {
            String message = "当前沙箱排队较长（约 " + Math.max(1, estimatedWaitMs / 1000)
                    + " 秒）。是否继续等待？如果继续，请再次调用 runCode 并保持相同代码。";
            return new QueueDecision("queued", message, snapshot, estimatedWaitMs);
        }
        return null;
    }

    /**
     * Estimates the wait from the queue depth and free slots. This is intentionally conservative:
     * Piston exposes no queue API, so the gate's counters are the only observable signal.
     */
    private long estimatedWaitMs(SandboxGate.Snapshot snapshot) {
        if (snapshot.availableSlots() > 0 && snapshot.waiting() == 0) {
            return 0;
        }
        int slots = Math.max(1, snapshot.availableSlots());
        int ahead = snapshot.waiting();
        long batches = (ahead + slots) / slots;
        return batches * properties.agentQueueEstimateRunMs();
    }

    private static String statusOf(RunResponse response) {
        String status = response.status();
        if (status == null) {
            return "runtime_error";
        }
        return switch (status) {
            case "OK" -> "ok";
            case "CE" -> "compile_error";
            case "TO" -> "run_timeout";
            case "SE" -> "server_unavailable";
            case "RE", "SG", "OL" -> "runtime_error";
            default -> "runtime_error";
        };
    }

    private static String summary(String status, RunResponse response) {
        if ("ok".equals(status)) {
            Long wallTime = response.wallTimeMs();
            return wallTime == null ? "运行成功" : "运行成功 · " + wallTime + "ms";
        }
        if ("compile_error".equals(status)) {
            return "编译失败";
        }
        if ("run_timeout".equals(status)) {
            return "运行超时";
        }
        if ("server_unavailable".equals(status)) {
            return "运行服务器不可用";
        }
        return "运行失败";
    }

    private static String guardStatus(String code) {
        return switch (code) {
            case "rate_limited", "daily_quota", "queue_full", "queue_timeout", "unauthorized" -> code;
            case "user_busy" -> "queue_timeout";
            default -> "server_unavailable";
        };
    }

    private static String guardMessage(String status) {
        return switch (status) {
            case "rate_limited" -> "代码运行次数过多，请稍后再试。";
            case "daily_quota" -> "今日代码运行次数已用完，请明天再试。";
            case "queue_full" -> "沙箱队列已满，请稍后再试。";
            case "queue_timeout" -> "沙箱排队超时，请稍后再试。";
            case "unauthorized" -> "需要登录后才能运行代码。";
            default -> "代码运行服务器暂时不可用，请稍后再试。";
        };
    }

    private static String sandboxStatus(String code) {
        return switch (code) {
            case "unsupported_language", "empty_code", "code_too_large" -> "invalid_request";
            case "sandbox_unavailable", "sandbox_error" -> "server_unavailable";
            default -> "server_unavailable";
        };
    }

    private static String sandboxMessage(String status) {
        return switch (status) {
            case "invalid_request" -> "代码请求无效：请检查语言和源码是否完整。";
            default -> "代码运行服务器暂时不可用，请稍后再试。";
        };
    }

    private static String resultJson(String status, RunResponse response, int attempt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("stdout", response.stdout());
        body.put("stderr", response.stderr());
        body.put("exitCode", response.exitCode());
        body.put("wallTimeMs", response.wallTimeMs());
        body.put("memoryBytes", response.memoryBytes());
        body.put("message", userMessage(status));
        body.put("attempt", attempt);
        return JsonUtil.toJson(body);
    }

    private static String userMessage(String status) {
        return switch (status) {
            case "compile_error" -> "编译失败，请根据 stderr 修正代码后重试。";
            case "run_timeout" -> "运行超时，请检查是否有死循环或过大的输入。";
            case "runtime_error" -> "运行失败，请根据 stderr 修正代码后重试。";
            case "server_unavailable" -> "代码运行服务器暂时不可用，请稍后再试。";
            default -> null;
        };
    }

    private static String errorJson(String status, String message, Integer attempt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        body.put("attempt", attempt);
        return JsonUtil.toJson(body);
    }

    private static void record(AgentTrace trace, String language, String code, String status,
            String summary) {
        if (trace != null) {
            trace.recordTool("runCode", argument(language, code), "ok".equals(status) ? 1 : 0,
                    status, summary);
        }
    }

    private static String argument(String language, String code) {
        String firstLine = Objects.requireNonNullElse(code, "").strip().lines()
                .findFirst()
                .orElse("")
                .strip();
        String value = Objects.requireNonNullElse(language, "") + " · " + firstLine;
        return value.length() <= MAX_ARGUMENT_CHARS
                ? value
                : value.substring(0, MAX_ARGUMENT_CHARS) + "…";
    }

    private static AgentTrace trace(ToolContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
        return value instanceof AgentTrace agentTrace ? agentTrace : null;
    }

    /** A queue decision that prevents execution until the user confirms. */
    private record QueueDecision(String status, String message, SandboxGate.Snapshot snapshot,
            long estimatedWaitMs) {

        private String toJson() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            body.put("message", message);
            body.put("waiting", snapshot.waiting());
            body.put("availableSlots", snapshot.availableSlots());
            body.put("estimatedWaitMs", estimatedWaitMs);
            return JsonUtil.toJson(body);
        }
    }
}
