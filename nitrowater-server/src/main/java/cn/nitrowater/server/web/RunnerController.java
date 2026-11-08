package cn.nitrowater.server.web;

import cn.nitrowater.server.service.sandbox.SandboxService;
import cn.nitrowater.server.service.sandbox.model.RunCommand;
import cn.nitrowater.server.web.dto.RunReq;
import cn.nitrowater.server.web.vo.RunResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs user code on the server-side sandbox (Piston).
 *
 * <p>{@code POST /api/sandbox/run} requires a valid SSO token: anonymous callers run code only in
 * the browser (WASM) and must not reach the backend. Rate limiting is enforced by an interceptor
 * (see {@code SandboxRateLimitInterceptor}) so this controller stays a thin pass-through.</p>
 */
@RestController
@RequestMapping("/api")
public class RunnerController {

    private final SandboxService sandboxService;

    public RunnerController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    /** Runs code for the authenticated caller. */
    @PostMapping("/sandbox/run")
    public RunResp run(@Valid @RequestBody RunReq request) {
        return RunResp.from(sandboxService.run(
                new RunCommand(request.language(), request.code(), request.stdin())));
    }
}
