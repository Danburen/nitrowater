package cn.nitrowater.server.web;

import cn.nitrowater.server.service.problem.ProblemSetService;
import cn.nitrowater.server.web.dto.ProblemSetItemsReq;
import cn.nitrowater.server.web.dto.ProblemSetResp;
import cn.nitrowater.server.web.dto.UserProblemSetUpsertReq;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User-owned problem set ("我的题单") endpoints. Login required; every write is owner-scoped
 * (a non-owner gets 403, and curated sets are not editable here).
 *
 * <ul>
 *   <li>{@code GET /api/me/problem-sets} — my sets;</li>
 *   <li>{@code POST /api/me/problem-sets} — create (owner = caller);</li>
 *   <li>{@code PUT /api/me/problem-sets/{id}} — rename / re-describe / change visibility;</li>
 *   <li>{@code DELETE /api/me/problem-sets/{id}} — delete;</li>
 *   <li>{@code PUT /api/me/problem-sets/{id}/problems} — replace the ordered membership.</li>
 * </ul>
 *
 * <p>Curated/admin writes live in {@link AdminProblemSetController}.</p>
 */
@RestController
public class MeProblemSetController {

    private final ProblemSetService problemSetService;

    public MeProblemSetController(ProblemSetService problemSetService) {
        this.problemSetService = problemSetService;
    }

    /** List the caller's problem sets. */
    @GetMapping("/api/me/problem-sets")
    public List<ProblemSetResp> listMine(@AuthenticationPrincipal Jwt jwt) {
        return problemSetService.listMine(jwt);
    }

    /** One of the caller's own problem sets (owner only). */
    @GetMapping("/api/me/problem-sets/{id}")
    public ProblemSetResp getMine(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return problemSetService.getMine(jwt, id);
    }

    /** Create a problem set owned by the caller. */
    @PostMapping("/api/me/problem-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemSetResp create(@Valid @RequestBody UserProblemSetUpsertReq request,
            @AuthenticationPrincipal Jwt jwt) {
        return problemSetService.createForUser(jwt, request);
    }

    /** Update name / description / visibility (owner only). */
    @PutMapping("/api/me/problem-sets/{id}")
    public ProblemSetResp update(@PathVariable Long id, @Valid @RequestBody UserProblemSetUpsertReq request,
            @AuthenticationPrincipal Jwt jwt) {
        return problemSetService.updateForUser(jwt, id, request);
    }

    /** Delete a set (owner only). */
    @DeleteMapping("/api/me/problem-sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        problemSetService.deleteForUser(jwt, id);
    }

    /** Replace the set's ordered membership with the given problem ids (owner only). */
    @PutMapping("/api/me/problem-sets/{id}/problems")
    public ProblemSetResp replaceItems(@PathVariable Long id, @Valid @RequestBody ProblemSetItemsReq request,
            @AuthenticationPrincipal Jwt jwt) {
        return problemSetService.replaceItems(jwt, id, request.problemIds());
    }
}
