package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.admin.RegisteredClientAdminService;
import cn.nitrowater.account.admin.dto.ClientCreateReq;
import cn.nitrowater.account.admin.dto.ClientRegistrationVO;
import cn.nitrowater.account.admin.dto.ClientSecretVO;
import cn.nitrowater.account.admin.dto.ClientUpdateReq;
import cn.nitrowater.account.admin.dto.ClientVO;
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
 * OIDC application (client) management for the admin console. Access is restricted to
 * {@code ROLE_SUPER_ADMIN} by the Servlet security chain ({@code /api/admin/**}).
 *
 * <p>Confidential secrets are shown only once (create / reset); afterward only a hash is stored.</p>
 */
@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
public class AdminClientController {

    private final RegisteredClientAdminService clientAdminService;

    @Operation(summary = "List all registered applications")
    @GetMapping
    public ApiResponse<List<ClientVO>> list() {
        return ApiResponse.success(clientAdminService.listClients());
    }

    @Operation(summary = "Get one application by id")
    @GetMapping("/{id}")
    public ApiResponse<ClientVO> get(@PathVariable String id) {
        return ApiResponse.success(clientAdminService.getClient(id));
    }

    @Operation(summary = "Register a new application (returns the secret once for a confidential client)")
    @PostMapping
    public ApiResponse<ClientRegistrationVO> create(@Valid @RequestBody ClientCreateReq body) {
        return ApiResponse.success(clientAdminService.createClient(body));
    }

    @Operation(summary = "Update an application (collections replaced; client_id immutable)")
    @PutMapping("/{id}")
    public ApiResponse<ClientRegistrationVO> update(@PathVariable String id, @Valid @RequestBody ClientUpdateReq body) {
        return ApiResponse.success(clientAdminService.updateClient(id, body));
    }

    @Operation(summary = "Rotate a confidential application's secret (returns it once)")
    @PostMapping("/{id}/secret")
    public ApiResponse<ClientSecretVO> resetSecret(@PathVariable String id) {
        return ApiResponse.success(clientAdminService.resetSecret(id));
    }

    @Operation(summary = "Delete an application (first-party clients are protected)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        clientAdminService.deleteClient(id);
        return ApiResponse.success();
    }
}
