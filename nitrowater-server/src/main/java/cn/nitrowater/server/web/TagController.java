package cn.nitrowater.server.web;

import cn.nitrowater.server.service.problem.TagService;
import cn.nitrowater.server.web.admin.AdminTagController;
import cn.nitrowater.server.web.dto.TagResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public tag endpoint (anonymous read).
 *
 * <ul>
 *   <li>{@code GET /api/public/tags} — list enabled tags.</li>
 * </ul>
 *
 * <p>Admin writes live in {@link AdminTagController}.</p>
 */
@RestController
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** Anonymous: list enabled tags. */
    @GetMapping("/api/public/tags")
    public List<TagResp> listPublic() {
        return tagService.listEnabled();
    }
}
