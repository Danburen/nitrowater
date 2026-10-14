package cn.nitrowater.server.web.admin;

import cn.nitrowater.server.service.problem.ProblemSetService;
import cn.nitrowater.server.web.dto.ProblemSetResp;
import cn.nitrowater.server.web.dto.ProblemSetUpsertReq;
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
 * Admin problem set ("题单") endpoints (CRUD).
 *
 * <p>The whole controller requires {@code ROLE_ADMIN}.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminProblemSetController {

    private final ProblemSetService problemSetService;

    public AdminProblemSetController(ProblemSetService problemSetService) {
        this.problemSetService = problemSetService;
    }

    /** Admin: list all problem sets. */
    @GetMapping("/api/problem-sets")
    public List<ProblemSetResp> listAll() {
        return problemSetService.listAll();
    }

    /** Admin: create a problem set. */
    @PostMapping("/api/problem-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemSetResp create(@Valid @RequestBody ProblemSetUpsertReq request) {
        return problemSetService.create(request);
    }

    /** Admin: update a problem set. */
    @PutMapping("/api/problem-sets/{id}")
    public ProblemSetResp update(@PathVariable Long id, @Valid @RequestBody ProblemSetUpsertReq request) {
        return problemSetService.update(id, request);
    }

    /** Admin: delete a problem set. */
    @DeleteMapping("/api/problem-sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        problemSetService.delete(id);
    }
}
