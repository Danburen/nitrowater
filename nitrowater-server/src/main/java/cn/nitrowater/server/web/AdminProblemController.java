package cn.nitrowater.server.web;

import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.service.problem.ProblemService;
import cn.nitrowater.server.web.dto.AdminProblemDetailResp;
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
 * Admin problem endpoints (create / update / test cases).
 *
 * <p>The whole controller requires {@code ROLE_ADMIN}.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminProblemController {

    private final ProblemService problemService;

    public AdminProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /** Admin: list every problem (disabled ones included). */
    @GetMapping("/api/problems")
    public List<ProblemSummaryResp> listAll() {
        return problemService.listAll();
    }

    /** Admin: full detail of one problem (hidden test cases included). */
    @GetMapping("/api/problems/{id}")
    public AdminProblemDetailResp detail(@PathVariable Long id) {
        return problemService.adminDetail(id);
    }

    /** Admin: create a problem. */
    @PostMapping("/api/problems")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemSummaryResp create(@Valid @RequestBody ProblemUpsertReq request,
            @AuthenticationPrincipal Jwt jwt) {
        return problemService.create(request, BizUserService.uidOf(jwt));
    }

    /** Admin: update a problem. */
    @PutMapping("/api/problems/{id}")
    public ProblemSummaryResp update(@PathVariable Long id, @Valid @RequestBody ProblemUpsertReq request) {
        return problemService.update(id, request);
    }

    /** Admin: replace a problem's test cases. */
    @PostMapping("/api/problems/{id}/testcases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceTestCases(@PathVariable Long id,
            @Valid @RequestBody List<TestCaseUpsertReq> cases) {
        problemService.replaceTestCases(id, cases);
    }
}
