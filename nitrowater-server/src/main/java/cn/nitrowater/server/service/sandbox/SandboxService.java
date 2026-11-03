package cn.nitrowater.server.service.sandbox;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.third.PistonClient;
import cn.nitrowater.server.infra.exception.SandboxException;
import cn.nitrowater.server.web.dto.RunRequest;
import cn.nitrowater.server.web.dto.RunResponse;
import cn.nitrowater.server.service.shared.CompileErrors;
import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
import cn.nitrowater.server.service.shared.LanguagePrelude;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Turns a user run request into a sandbox execution and a normalised response.
 *
 * <p>Responsibilities: validate the request (known language, size), apply the tier's resource
 * limits, invoke {@link PistonClient}, and collapse the two-stage outcome into one result the
 * front-end can render. Captured output is capped at {@code maxOutputBytes} per stream so a
 * runaway program cannot flood the response. All Piston specifics stay behind
 * {@link PistonClient}.</p>
 */
@Service
@RequiredArgsConstructor
public class SandboxService {

    private final PistonClient pistonClient;
    private final SandboxProperties properties;
    /**
     * Runs {@code request} once and returns its output.
     *
     * @param request the front-end run request
     * @return the normalised run result
     * @throws SandboxException on invalid input or when the sandbox is unreachable/errored
     */
    public RunResponse run(RunRequest request) {
        LanguageMapper.Target target = LanguageMapper.resolve(request.language())
                .orElseThrow(() -> new SandboxException(
                        HttpStatus.BAD_REQUEST, "unsupported_language",
                        "Unsupported language: " + request.language()));

        if (StringUtil.isBlank(request.code())) {
            throw new SandboxException(HttpStatus.BAD_REQUEST, "empty_code", "Code must not be empty");
        }
        if (request.code().getBytes(StandardCharsets.UTF_8).length > properties.maxCodeBytes()) {
            throw new SandboxException(HttpStatus.BAD_REQUEST, "code_too_large",
                    "Code exceeds the maximum size of " + properties.maxCodeBytes() + " bytes");
        }

        // The prelude lets callers skip boilerplate imports.
        ExecSpec spec = new ExecSpec(
                target.pistonLanguage(),
                target.version(),
                target.fileName(),
                LanguagePrelude.wrap(target.pistonLanguage(), request.code()),
                request.stdin() == null ? "" : request.stdin(),
                properties.compileTimeoutMs(),
                properties.runTimeoutMs(),
                properties.memoryLimitBytes(),
                properties.memoryLimitBytes());

        ExecResult result;
        try {
            result = pistonClient.execute(spec);
        } catch (ResourceAccessException ex) {
            throw new SandboxException(HttpStatus.SERVICE_UNAVAILABLE, "sandbox_unavailable",
                    "Sandbox service is unavailable");
        } catch (RestClientResponseException ex) {
            throw new SandboxException(HttpStatus.BAD_GATEWAY, "sandbox_error",
                    ex.getResponseBodyAsString());
        }

        return toResponse(target.pistonLanguage(), result);
    }

    /**
     * Collapses the Piston compile/run stages into a single front-end response.
     *
     * <p>Compile failures are recognised through {@link CompileErrors} so languages without a
     * dedicated compile stage (Java, Python) still surface {@code CE} with the compiler output,
     * instead of masquerading as a runtime error.</p>
     */
    private RunResponse toResponse(String pistonLanguage, ExecResult result) {
        String diagnostic = CompileErrors.diagnostic(pistonLanguage, result);
        if (diagnostic != null) {
            ExecResult.Stage compile = result.compile() != null ? result.compile() : result.run();
            Long wallTime = compile == null ? null : compile.wallTimeMs();
            Long memory = compile == null ? null : compile.memoryBytes();
            Integer exitCode = compile == null ? null : compile.code();
            return new RunResponse(null, truncate(diagnostic), "CE", exitCode, wallTime, memory,
                    "compilation failed");
        }
        ExecResult.Stage run = result.run() != null ? result.run() : result.compile();
        if (run == null) {
            return new RunResponse(null, null, "SE", null, null, null, "No execution result returned");
        }
        String status = run.status() == null ? "OK" : run.status();
        return new RunResponse(truncate(run.stdout()), truncate(run.stderr()), status, run.code(),
                run.wallTimeMs(), run.memoryBytes(), run.message());
    }

    /**
     * Caps a captured stream at {@code maxOutputBytes}, appending a marker when it was cut.
     *
     * <p>The cut is made on a code-point boundary so a multi-byte character is never split; the
     * comparison uses UTF-8 bytes because that is what leaves the service.</p>
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        int max = properties.maxOutputBytes();
        if (text.getBytes(StandardCharsets.UTF_8).length <= max) {
            return text;
        }
        int bytes = 0;
        int end = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            int size = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (bytes + size > max) {
                break;
            }
            bytes += size;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end) + "\n...[output truncated]";
    }
}
