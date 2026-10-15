package cn.nitrowater.server.web;

import cn.nitrowater.server.service.judge.PlaygroundRunService;
import cn.nitrowater.server.web.dto.PlaygroundRunRequest;
import cn.nitrowater.server.web.dto.PlaygroundRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs user code against user-supplied samples (a self-test) without recording a submission.
 *
 * <p>{@code POST /api/sandbox/run-problem} requires a valid SSO token and shares the
 * {@code /api/sandbox/**} rate-limit interceptor with {@code /api/sandbox/run}.</p>
 */
@RestController
public class PlaygroundRunController {

    private final PlaygroundRunService playgroundRunService;

    public PlaygroundRunController(PlaygroundRunService playgroundRunService) {
        this.playgroundRunService = playgroundRunService;
    }

    /** Runs the code against the supplied samples (or the problem's sample cases). */
    @PostMapping("/api/sandbox/run-problem")
    public PlaygroundRunResponse run(@Valid @RequestBody PlaygroundRunRequest request) {
        return playgroundRunService.run(request);
    }
}
