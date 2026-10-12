package cn.nitrowater.server.web;

import cn.nitrowater.server.service.problem.ProblemService;
import cn.nitrowater.server.web.dto.ProblemDetailResp;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public problem endpoints (anonymous read).
 *
 * <ul>
 *   <li>{@code GET /api/public/problems} — list enabled problems;</li>
 *   <li>{@code GET /api/public/problems/{id}} — problem detail (sample cases only).</li>
 * </ul>
 *
 * <p>Admin writes live in {@link AdminProblemController}.</p>
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
}
