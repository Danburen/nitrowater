package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.admin.RoleAdminService;
import cn.nitrowater.account.admin.dto.RoleCreateReq;
import cn.nitrowater.account.admin.dto.RoleUpdateReq;
import cn.nitrowater.account.admin.dto.RoleVO;
import cn.nitrowater.lib.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role CRUD for the admin console. Access is restricted to {@code ROLE_SUPER_ADMIN} by the
 * Servlet security chain ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleAdminService roleAdminService;

    @Operation(summary = "Paged role listing with bound-account counts")
    @GetMapping
    public ApiResponse<Page<RoleVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean builtin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(roleAdminService.listRoles(keyword, builtin, page, size));
    }

    @Operation(summary = "Create a custom role")
    @PostMapping
    public ApiResponse<RoleVO> create(@Valid @RequestBody RoleCreateReq body) {
        return ApiResponse.success(roleAdminService.createRole(body));
    }

    @Operation(summary = "Update a role's name / description (code is immutable)")
    @PutMapping("/{id}")
    public ApiResponse<RoleVO> update(@PathVariable long id, @Valid @RequestBody RoleUpdateReq body) {
        return ApiResponse.success(roleAdminService.updateRole(id, body));
    }

    @Operation(summary = "Delete a custom role (rejected for built-in or in-use roles)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        roleAdminService.deleteRole(id);
        return ApiResponse.success();
    }
}
