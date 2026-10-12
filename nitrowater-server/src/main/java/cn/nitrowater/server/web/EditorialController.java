package cn.nitrowater.server.web;

import cn.nitrowater.server.service.problem.EditorialService;
import cn.nitrowater.server.web.dto.EditorialDetailResp;
import cn.nitrowater.server.web.dto.EditorialSummaryResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public editorial endpoints (anonymous read, published only).
 *
 * <ul>
 *   <li>{@code GET /api/public/problems/{id}/editorials?sort=hot|new} — published editorials of a problem;</li>
 *   <li>{@code GET /api/public/editorials/{id}} — one published editorial (increments its view count).</li>
 * </ul>
 *
 * <p>Admin writes live in {@link AdminEditorialController}.</p>
 */
@RestController
public class EditorialController {

    private final EditorialService editorialService;

    public EditorialController(EditorialService editorialService) {
        this.editorialService = editorialService;
    }

    /** Anonymous: published editorials of a problem. */
    @GetMapping("/api/public/problems/{problemId}/editorials")
    public List<EditorialSummaryResp> listPublic(@PathVariable Long problemId,
            @RequestParam(name = "sort", required = false) String sort) {
        return editorialService.listPublished(problemId, sort);
    }

    /** Anonymous: one published editorial (increments its view count). */
    @GetMapping("/api/public/editorials/{id}")
    public EditorialDetailResp getPublic(@PathVariable Long id) {
        return editorialService.getPublished(id);
    }
}
