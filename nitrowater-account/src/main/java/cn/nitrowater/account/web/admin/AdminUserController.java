package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.admin.RoleAdminService;
import cn.nitrowater.account.admin.dto.AssignRolesReq;
import cn.nitrowater.account.admin.dto.PageResult;
import cn.nitrowater.account.admin.dto.UserSummaryVO;
import cn.nitrowater.lib.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account listing + role assignment for the admin console. Access is restricted to
 * {@code ROLE_SUPER_ADMIN} by the Servlet security chain ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final RoleAdminService roleAdminService;

    @Operation(summary = "Paged account search by username / nickname, each row carrying its role codes")
    @GetMapping
    public ApiResponse<PageResult<UserSummaryVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(roleAdminService.listUsers(keyword, page, size));
    }

    @Operation(summary = "Role codes bound to one account")
    @GetMapping("/{uid}/roles")
    public ApiResponse<List<String>> roles(@PathVariable long uid) {
        return ApiResponse.success(roleAdminService.getUserRoles(uid));
    }

    @Operation(summary = "Replace an account's role set")
    @PutMapping("/{uid}/roles")
    public ApiResponse<List<String>> assign(@PathVariable long uid, @Valid @RequestBody AssignRolesReq body) {
        return ApiResponse.success(roleAdminService.setUserRoles(uid, body));
    }
}
