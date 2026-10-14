package cn.nitrowater.server.web;

import cn.nitrowater.server.service.problem.ProblemSetService;
import cn.nitrowater.server.web.admin.AdminProblemSetController;
import cn.nitrowater.server.web.dto.ProblemSetResp;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public problem set ("题单") endpoints (anonymous read).
 *
 * <ul>
 *   <li>{@code GET /api/public/problem-sets} — list enabled sets;</li>
 *   <li>{@code GET /api/public/problem-sets/{id}} — one enabled set;</li>
 *   <li>{@code GET /api/public/problem-sets/{id}/problems} — enabled problems of a set.</li>
 * </ul>
 *
 * <p>Admin writes live in {@link AdminProblemSetController}.</p>
 */
@RestController
public class ProblemSetController {

    private final ProblemSetService problemSetService;

    public ProblemSetController(ProblemSetService problemSetService) {
        this.problemSetService = problemSetService;
    }

    /** Anonymous: list enabled official problem sets. */
    @GetMapping("/api/public/problem-sets")
    public List<ProblemSetResp> listPublic() {
        return problemSetService.listEnabled();
    }

    /** Anonymous: public user-owned problem sets (题单广场). */
    @GetMapping("/api/public/problem-sets/square")
    public List<ProblemSetResp> listSquare() {
        return problemSetService.listPublicUserSets();
    }

    /** Anonymous: one enabled problem set. */
    @GetMapping("/api/public/problem-sets/{id}")
    public ProblemSetResp getPublic(@PathVariable Long id) {
        return problemSetService.getEnabled(id);
    }

    /** Anonymous: enabled problems of a set, in the set's order. */
    @GetMapping("/api/public/problem-sets/{id}/problems")
    public List<ProblemSummaryResp> listProblems(@PathVariable Long id) {
        return problemSetService.listProblems(id);
    }
}
