package cn.nitrowater.server.web;

import cn.nitrowater.server.service.problem.ProblemService;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.web.dto.ProblemDetailResp;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import cn.nitrowater.server.web.dto.ProblemUpsertReq;
import cn.nitrowater.server.web.dto.TestCaseUpsertReq;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Problem endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/public/problems} {@code /{id}} — anonymous read (enabled problems only);</li>
 *   <li>{@code POST /api/problems} {@code PUT /{id}} — admin (create/update);</li>
 *   <li>{@code POST /api/problems/{id}/testcases} — admin (replace test cases).</li>
 * </ul>
 */
@RestController
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /** Anonymous: list enabled problems. */
    @GetMapping("/api/public/problems")
    public List<ProblemSummaryResp> list() {
        return problemService.listEnabled();
    }

    /** Anonymous: problem detail (sample cases only). */
    @GetMapping("/api/public/problems/{id}")
    public ProblemDetailResp detail(@PathVariable Long id) {
        return problemService.detail(id);
    }

    /** Admin: create a problem. */
    @PostMapping("/api/problems")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ProblemSummaryResp create(@Valid @RequestBody ProblemUpsertReq request,
            @AuthenticationPrincipal Jwt jwt) {
        return problemService.create(request, BizUserService.uidOf(jwt));
    }

    /** Admin: update a problem. */
    @PutMapping("/api/problems/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ProblemSummaryResp update(@PathVariable Long id, @Valid @RequestBody ProblemUpsertReq request) {
        return problemService.update(id, request);
    }

    /** Admin: replace a problem's test cases. */
    @PostMapping("/api/problems/{id}/testcases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void replaceTestCases(@PathVariable Long id,
            @Valid @RequestBody List<TestCaseUpsertReq> cases) {
        problemService.replaceTestCases(id, cases);
    }
}
