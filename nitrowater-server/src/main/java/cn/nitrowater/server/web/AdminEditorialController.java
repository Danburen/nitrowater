package cn.nitrowater.server.web;

import cn.nitrowater.server.infra.enums.EditorialStatus;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.service.problem.EditorialService;
import cn.nitrowater.server.web.dto.EditorialDetailResp;
import cn.nitrowater.server.web.dto.EditorialSummaryResp;
import cn.nitrowater.server.web.dto.EditorialUpsertReq;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Admin editorial endpoints (CRUD + publish / pin transitions).
 *
 * <p>The whole controller requires {@code ROLE_ADMIN}.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminEditorialController {

    private final EditorialService editorialService;

    public AdminEditorialController(EditorialService editorialService) {
        this.editorialService = editorialService;
    }

    /** Admin: every editorial of a problem (all statuses). */
    @GetMapping("/api/problems/{problemId}/editorials")
    public List<EditorialSummaryResp> listAll(@PathVariable Long problemId) {
        return editorialService.listAll(problemId);
    }

    /** Admin: create an official editorial. */
    @PostMapping("/api/problems/{problemId}/editorials")
    @ResponseStatus(HttpStatus.CREATED)
    public EditorialDetailResp create(@PathVariable Long problemId,
            @Valid @RequestBody EditorialUpsertReq request, @AuthenticationPrincipal Jwt jwt) {
        return editorialService.create(problemId, request, BizUserService.uidOf(jwt));
    }

    /** Admin: update an editorial. */
    @PutMapping("/api/editorials/{id}")
    public EditorialDetailResp update(@PathVariable Long id, @Valid @RequestBody EditorialUpsertReq request) {
        return editorialService.update(id, request);
    }

    /** Admin: delete an editorial. */
    @DeleteMapping("/api/editorials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        editorialService.delete(id);
    }

    /** Admin: publish an editorial. */
    @PostMapping("/api/editorials/{id}/publish")
    public EditorialDetailResp publish(@PathVariable Long id) {
        return editorialService.changeStatus(id, EditorialStatus.PUBLISHED);
    }

    /** Admin: move an editorial back to draft. */
    @PostMapping("/api/editorials/{id}/unpublish")
    public EditorialDetailResp unpublish(@PathVariable Long id) {
        return editorialService.changeStatus(id, EditorialStatus.DRAFT);
    }

    /** Admin: pin an editorial. */
    @PostMapping("/api/editorials/{id}/pin")
    public EditorialDetailResp pin(@PathVariable Long id) {
        return editorialService.setPinned(id, true);
    }

    /** Admin: unpin an editorial. */
    @PostMapping("/api/editorials/{id}/unpin")
    public EditorialDetailResp unpin(@PathVariable Long id) {
        return editorialService.setPinned(id, false);
    }
}
