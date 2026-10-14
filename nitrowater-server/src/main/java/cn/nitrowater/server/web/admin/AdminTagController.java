package cn.nitrowater.server.web.admin;

import cn.nitrowater.server.service.problem.TagService;
import cn.nitrowater.server.web.dto.TagResp;
import cn.nitrowater.server.web.dto.TagUpsertReq;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Admin tag endpoints (CRUD).
 *
 * <p>The whole controller requires {@code ROLE_ADMIN}.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminTagController {

    private final TagService tagService;

    public AdminTagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** Admin: list all tags. */
    @GetMapping("/api/tags")
    public List<TagResp> listAll() {
        return tagService.listAll();
    }

    /** Admin: create a tag. */
    @PostMapping("/api/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagResp create(@Valid @RequestBody TagUpsertReq request) {
        return tagService.create(request);
    }

    /** Admin: update a tag. */
    @PutMapping("/api/tags/{id}")
    public TagResp update(@PathVariable Long id, @Valid @RequestBody TagUpsertReq request) {
        return tagService.update(id, request);
    }

    /** Admin: delete a tag. */
    @DeleteMapping("/api/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tagService.delete(id);
    }
}
