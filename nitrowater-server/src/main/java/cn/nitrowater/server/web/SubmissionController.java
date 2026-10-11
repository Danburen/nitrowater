package cn.nitrowater.server.web;

import cn.nitrowater.server.service.SubmissionService;
import cn.nitrowater.server.web.dto.CreateSubmissionReq;
import cn.nitrowater.server.web.dto.SubmissionResp;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Submission endpoints (login required).
 *
 * <ul>
 *   <li>{@code POST /api/submissions} — create + enqueue (202);</li>
 *   <li>{@code GET /api/submissions/{id}} — poll state/result;</li>
 *   <li>{@code GET /api/submissions} — the caller's submissions.</li>
 * </ul>
 */
@RestController
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /** Create + enqueue a submission. */
    @PostMapping("/api/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmissionResp create(@Valid @RequestBody CreateSubmissionReq request,
            @AuthenticationPrincipal Jwt jwt) {
        return submissionService.create(jwt, request);
    }

    /** Poll one submission. */
    @GetMapping("/api/submissions/{id}")
    public SubmissionResp get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return submissionService.get(jwt, id);
    }

    /** List the caller's submissions. */
    @GetMapping("/api/submissions")
    public List<SubmissionResp> listMine(@AuthenticationPrincipal Jwt jwt) {
        return submissionService.listMine(jwt);
    }
}
