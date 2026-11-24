package cn.nitrowater.server.web.admin;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.enums.InstanceCredentialStatus;
import cn.nitrowater.server.infra.exception.InstanceException;
import cn.nitrowater.server.service.instance.InstanceCredentialService;
import cn.nitrowater.server.service.instance.InstanceRegistryService;
import cn.nitrowater.server.web.dto.InstanceCredentialCreateReq;
import cn.nitrowater.server.web.dto.InstanceRenameReq;
import cn.nitrowater.server.web.vo.InstanceCredentialIssueResp;
import cn.nitrowater.server.web.vo.InstanceCredentialResp;
import cn.nitrowater.server.web.vo.InstanceRegistryResp;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Admin endpoints for VeloChatX instance api-keys and their instance registry.
 *
 * <p>The whole controller lives under {@code /api/admin/**} (chain-level role rule) and additionally
 * requires {@code ROLE_ADMIN} at method level. Issuing a key returns the plaintext exactly once;
 * all other views are masked.</p>
 */
@RestController
@RequestMapping("/api/admin/instance-credentials")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminInstanceCredentialController {

    private final InstanceCredentialService credentialService;
    private final InstanceRegistryService registryService;

    public AdminInstanceCredentialController(InstanceCredentialService credentialService,
            InstanceRegistryService registryService) {
        this.credentialService = credentialService;
        this.registryService = registryService;
    }

    /**
     * Admin: paged, masked list of instance credentials.
     *
     * @param keyword optional kid / prefix / remark substring
     * @param status  optional lifecycle filter ({@code ACTIVE} / {@code ROTATED} / {@code REVOKED})
     * @param page    zero-based page index
     * @param size    page size
     * @return one page of credentials
     */
    @GetMapping
    public ApiResponse<Page<InstanceCredentialResp>> list(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(credentialService.list(keyword, parseStatus(status), page, size)
                .map(InstanceCredentialResp::from));
    }

    /**
     * Admin: issue a user-level api-key. The plaintext appears only in this response.
     *
     * @param request issuance payload
     * @return the masked credential plus the one-time plaintext key
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InstanceCredentialIssueResp> issue(
            @Valid @RequestBody InstanceCredentialCreateReq request) {
        return ApiResponse.success(InstanceCredentialIssueResp.from(
                credentialService.issue(request.remark(), request.ownerUid(), request.toScopes())));
    }

    /**
     * Admin: revoke a credential. Its instances stay registered but the key stops authenticating.
     *
     * @param id credential primary key
     * @return the updated masked credential
     */
    @PostMapping("/{id}/revoke")
    public ApiResponse<InstanceCredentialResp> revoke(@PathVariable Long id) {
        return ApiResponse.success(InstanceCredentialResp.from(credentialService.revoke(id)));
    }

    /**
     * Admin: paged list of the live instances registered under a credential.
     *
     * @param id      credential primary key
     * @param keyword optional display name / UUID / boot id substring
     * @param page    zero-based page index
     * @param size    page size
     * @return one page of instances
     */
    @GetMapping("/{id}/instances")
    public ApiResponse<Page<InstanceRegistryResp>> instances(
            @PathVariable Long id,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        InstanceCredential credential = credentialService.require(id);
        return ApiResponse.success(registryService.list(credential, keyword, page, size)
                .map(InstanceRegistryResp::from));
    }

    /**
     * Admin: rename an instance. Names are unique per owner so name-based addressing stays clear.
     *
     * @param id          credential primary key
     * @param instanceId  instance UUID
     * @param request     new display name
     * @return the updated instance
     */
    @PostMapping("/{id}/instances/{instanceId}/rename")
    public ApiResponse<InstanceRegistryResp> rename(
            @PathVariable Long id,
            @PathVariable String instanceId,
            @Valid @RequestBody InstanceRenameReq request) {
        InstanceCredential credential = credentialService.require(id);
        return ApiResponse.success(InstanceRegistryResp.from(
                registryService.rename(credential, instanceId, request.displayName())));
    }

    /**
     * Admin: remove an instance, releasing its slot. The instance may re-bind later with a new name.
     *
     * @param id         credential primary key
     * @param instanceId instance UUID
     * @return an empty success envelope
     */
    @PostMapping("/{id}/instances/{instanceId}/remove")
    public ApiResponse<Void> remove(@PathVariable Long id, @PathVariable String instanceId) {
        InstanceCredential credential = credentialService.require(id);
        registryService.remove(credential, instanceId);
        return ApiResponse.success();
    }

    /** @return the status named by {@code value}, or {@code null} when absent */
    private static InstanceCredentialStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return InstanceCredentialStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw InstanceException.badProtocol("Unknown credential status: " + value);
        }
    }
}
