package cn.nitrowater.server.service.ai.tool;

import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import cn.nitrowater.server.service.ai.model.tool.ToolResponse;
import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.exception.SandboxException;
import cn.nitrowater.server.infra.ratelimit.SandboxGate;
import cn.nitrowater.server.service.sandbox.SandboxExecutionGuard;
import cn.nitrowater.server.web.dto.RunRequest;
import cn.nitrowater.server.web.dto.RunResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(RunCodeTool.class);

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
     * @return JSON {@code {success,code,message,data}} with normalised outcome, stdout/stderr and
     *         timing information in {@code data}
     */
    @Tool(name = "runCode", description = """
            在服务端沙箱中编译并运行代码，返回 stdout、stderr、退出码和耗时。
            仅在用户明确要求“运行 / 跑一下 / 测试 / 验证”代码，或你必须实际执行才能确认结果时调用。
            支持 java、c、cpp、python、javascript、typescript、lua。
            编译或运行失败时，根据 stderr 修正代码后再试；本次对话最多运行 3 次（含首次），超出后必须停止，不要重复提交完全相同的代码。
            如果返回 code=tool.queued，说明队列较长，先询问用户是否继续等待，不要直接再次调用。
            """)
    public String runCode(
            @ToolParam(description = "语言：java / c / cpp / python / javascript / typescript / lua")
            String language,
            @ToolParam(description = "完整可运行源码；不要包含 Markdown 代码围栏")
            String code,
            @ToolParam(description = "标准输入，可选", required = false)
            String stdin,
            ToolContext context) {
        AgentTrace trace = null;
        if(context != null){
            Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
            trace = value instanceof AgentTrace agentTrace ? agentTrace : null;
        }

        String uid = trace == null ? null : trace.userId();
        String argument = argument(language, code);
        if (StringUtil.isBlank(uid)) {
            return ToolResponse.of(ToolCode.UNAUTHORIZED, "需要登录后才能运行代码。")
                    .record(trace, "runCode", argument, 0)
                    .json();
        }

        QueueDecision queue = queueDecision();
        if (queue != null) {
            return queue.toResponse().record(trace, "runCode", argument, 0).json();
        }

        int attempt = trace.nextRunCodeAttempt();
        if (attempt > MAX_ATTEMPTS) {
            return ToolResponse.of(ToolCode.RETRY_EXHAUSTED,
                            "本次对话中 runCode 已尝试 " + MAX_ATTEMPTS
                                    + " 次，仍未成功。请停止重试，向用户说明失败原因并给出修复建议。",
                            Map.of("attempt", attempt))
                    .record(trace, "runCode", argument, 0)
                    .json();
        }

        try {
            RunResponse response = guard.execute(uid, new RunRequest(language, code, stdin),
                    properties.agentQueueWaitMs());
            ToolCode outcome = getOutcome(response);

            String message = switch (outcome) {
                case OK -> {
                    Long wallTime = response.wallTimeMs();
                    yield wallTime == null ? "运行成功" : "运行成功 · " + wallTime + "ms";
                }
                case COMPILE_ERROR -> "编译失败，请根据 stderr 修正代码后重试。";
                case RUN_TIMEOUT -> "运行超时，请检查是否有死循环或过大的输入。";
                case RUNTIME_ERROR -> "运行失败，请根据 stderr 修正代码后重试。";
                case UNAVAILABLE -> "代码运行服务器暂时不可用，请稍后再试。";
                default -> null;
            };

            Map<String, Object> data  = new LinkedHashMap<>();
            data.put("stdout", response.stdout());
            data.put("stderr", response.stderr());
            data.put("exitCode", response.exitCode());
            data.put("wallTimeMs", response.wallTimeMs());
            data.put("memoryBytes", response.memoryBytes());
            data.put("attempt", attempt);
            return ToolResponse.of(outcome, message, data)
                    .record(trace, "runCode", argument, outcome == ToolCode.OK ? 1 : 0)
                    .json();

        } catch (SandboxExecutionGuard.GuardException ex) {
            ToolCode outcome = switch (ex.getCode()) {
                case "rate_limited" -> ToolCode.RATE_LIMITED;
                case "daily_quota" -> ToolCode.DAILY_QUOTA;
                case "queue_full" -> ToolCode.QUEUE_FULL;
                case "queue_timeout", "user_busy" -> ToolCode.QUEUE_TIMEOUT;
                case "unauthorized" -> ToolCode.UNAUTHORIZED;
                default -> ToolCode.UNAVAILABLE;
            };
            String message = switch (outcome) {
                case RATE_LIMITED -> "代码运行次数过多，请稍后再试。";
                case DAILY_QUOTA -> "今日代码运行次数已用完，请明天再试。";
                case QUEUE_FULL -> "沙箱队列已满，请稍后再试。";
                case QUEUE_TIMEOUT -> "沙箱排队超时，请稍后再试。";
                case UNAUTHORIZED -> "需要登录后才能运行代码。";
                default -> "代码运行服务器暂时不可用，请稍后再试。";
            };
            return ToolResponse.of(outcome, message, Map.of("attempt", attempt))
                    .record(trace, "runCode", argument, 0)
                    .json();
        } catch (SandboxException ex) {
            ToolCode outcome = switch (ex.getCode()) {
                case "unsupported_language", "empty_code", "code_too_large" -> ToolCode.INVALID_REQUEST;
                default -> ToolCode.UNAVAILABLE;
            };
            String message =  switch (outcome) {
                case INVALID_REQUEST -> "代码请求无效：请检查语言和源码是否完整。";
                default -> "代码运行服务器暂时不可用，请稍后再试。";
            };
            return ToolResponse.of(outcome, message, Map.of("attempt", attempt))
                    .record(trace, "runCode", argument, 0)
                    .json();
        } catch (Exception ex) {
            LOG.warn("runCode execution failed", ex);
            return ToolResponse.of(
                    ToolCode.UNAVAILABLE, "代码运行服务器暂时不可用，请稍后再试。",
                            Map.of("attempt", attempt)
                    ).record(trace, "runCode", argument, 0)
                    .json();
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
            return new QueueDecision(
                    ToolCode.QUEUE_FULL,
                    "沙箱队列已满，请稍后再试。",
                    snapshot,
                    0
            );
        }

        long estimatedWaitMs;
        if(snapshot.availableSlots() > 0 && snapshot.waiting() == 0) {
            estimatedWaitMs = 0;
        } else {
            int slots = Math.max(1, snapshot.availableSlots());
            int ahead = snapshot.waiting();
            long batches = (ahead + slots) / slots;
            estimatedWaitMs = batches * properties.agentQueueEstimateRunMs();
        }

        boolean tooManyWaiting = snapshot.waiting() >= properties.agentQueueAskWaiting();
        boolean tooLong = estimatedWaitMs >= properties.agentQueueAskMs();
        if (tooManyWaiting || tooLong) {
            String message = "当前沙箱排队较长（约 " + Math.max(1, estimatedWaitMs / 1000)
                    + " 秒）。是否继续等待？如果继续，请再次调用 runCode 并保持相同代码。";
            return new QueueDecision(ToolCode.QUEUED, message, snapshot, estimatedWaitMs);
        }
        return null;
    }

    /** @return the outcome code for a completed run */
    private static ToolCode getOutcome(RunResponse response) {
        String status = response.status();
        if (status == null) {
            return ToolCode.RUNTIME_ERROR;
        }
        return switch (status) {
            case "OK" -> ToolCode.OK;
            case "CE" -> ToolCode.COMPILE_ERROR;
            case "TO" -> ToolCode.RUN_TIMEOUT;
            case "SE" -> ToolCode.UNAVAILABLE;
            default -> ToolCode.RUNTIME_ERROR;
        };
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
    private record QueueDecision(
            ToolCode code,
            String message,
            SandboxGate.Snapshot snapshot,
            long estimatedWaitMs
    ) {
        private ToolResponse toResponse() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("waiting", snapshot.waiting());
            data.put("availableSlots", snapshot.availableSlots());
            data.put("estimatedWaitMs", estimatedWaitMs);
            return ToolResponse.of(code, message, data);
        }
    }
}
