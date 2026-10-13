package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.admin.RoleAdminService;
import cn.nitrowater.account.admin.dto.RoleCreateReq;
import cn.nitrowater.account.admin.dto.RoleUpdateReq;
import cn.nitrowater.account.admin.dto.RoleVO;
import cn.nitrowater.lib.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Role CRUD for the admin console. Access is restricted to {@code ROLE_SUPER_ADMIN} by the
 * Servlet security chain ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleAdminService roleAdminService;

    @Operation(summary = "List all roles with bound-account counts")
    @GetMapping
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.success(roleAdminService.listRoles());
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
