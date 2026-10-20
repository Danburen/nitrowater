package cn.nitrowater.server.web;

import cn.nitrowater.server.service.judge.PlaygroundRunService;
import cn.nitrowater.server.web.dto.PlaygroundRunRequest;
import cn.nitrowater.server.web.dto.PlaygroundRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs user code once with a free-form input (a self-test) without recording a submission or
 * judging the output.
 *
 * <p>{@code POST /api/sandbox/run-problem} returns the program's raw stdout under the problem's
 * harness/limits; nothing is compared and nothing is persisted. It requires a valid SSO token and
 * shares the {@code /api/sandbox/**} rate-limit interceptor with {@code /api/sandbox/run}.</p>
 */
@RestController
public class PlaygroundRunController {

    private final PlaygroundRunService playgroundRunService;

    public PlaygroundRunController(PlaygroundRunService playgroundRunService) {
        this.playgroundRunService = playgroundRunService;
    }

    /** Runs the code once with the supplied input and returns its output (no judging). */
    @PostMapping("/api/sandbox/run-problem")
    public PlaygroundRunResponse run(@Valid @RequestBody PlaygroundRunRequest request) {
        return playgroundRunService.run(request);
    }
}
