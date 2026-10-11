package cn.nitrowater.server.sandbox;

import cn.nitrowater.server.sandbox.web.RunRequest;
import cn.nitrowater.server.sandbox.web.RunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

/**
 * Turns a user run request into a sandbox execution and a normalised response.
 *
 * <p>Responsibilities: validate the request (known language, size), apply the tier's resource
 * limits, invoke {@link PistonClient}, and collapse the two-stage outcome into one result the
 * front-end can render. All Piston specifics stay behind {@link PistonClient}.</p>
 */
@Service
public class SandboxService {

    private final PistonClient pistonClient;
    private final SandboxProperties properties;

    public SandboxService(PistonClient pistonClient, SandboxProperties properties) {
        this.pistonClient = pistonClient;
        this.properties = properties;
    }

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

        if (request.code() == null || request.code().isBlank()) {
            throw new SandboxException(HttpStatus.BAD_REQUEST, "empty_code", "Code must not be empty");
        }
        if (request.code().getBytes(StandardCharsets.UTF_8).length > properties.maxCodeBytes()) {
            throw new SandboxException(HttpStatus.BAD_REQUEST, "code_too_large",
                    "Code exceeds the maximum size of " + properties.maxCodeBytes() + " bytes");
        }

        ExecSpec spec = new ExecSpec(
                target.pistonLanguage(),
                target.version(),
                target.fileName(),
                request.code(),
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

        return toResponse(result);
    }

    /** Collapses the Piston compile/run stages into a single front-end response. */
    private static RunResponse toResponse(ExecResult result) {
        ExecResult.Stage compile = result.compile();
        if (compile != null && compile.code() != null && compile.code() != 0) {
            return new RunResponse(null, compile.stderr(), "CE", compile.code(),
                    compile.wallTimeMs(), compile.memoryBytes(), compile.message());
        }
        ExecResult.Stage run = result.run() != null ? result.run() : compile;
        if (run == null) {
            return new RunResponse(null, null, "SE", null, null, null, "No execution result returned");
        }
        String status = run.status() == null ? "OK" : run.status();
        return new RunResponse(run.stdout(), run.stderr(), status, run.code(),
                run.wallTimeMs(), run.memoryBytes(), run.message());
    }
}
