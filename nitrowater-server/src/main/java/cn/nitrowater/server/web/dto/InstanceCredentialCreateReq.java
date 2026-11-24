package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.infra.enums.InstanceScope;
import cn.nitrowater.server.infra.exception.InstanceException;
import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Payload for issuing a user-level instance api-key (admin only).
 *
 * @param remark   optional admin note (purpose / machine hint)
 * @param ownerUid optional logical owner uid; the key fixes which user owns its instances
 * @param scopes   optional scope codes; unknown codes are rejected, empty means all scopes
 */
public record InstanceCredentialCreateReq(
        @Size(max = 255) String remark,
        Long ownerUid,
        List<String> scopes) {

    /**
     * @return the validated, de-duplicated scopes to grant
     * @throws InstanceException when a scope code is unknown
     */
    public Set<InstanceScope> toScopes() {
        if (scopes == null || scopes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<InstanceScope> result = new LinkedHashSet<>();
        for (String token : scopes) {
            String code = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            InstanceScope scope = Arrays.stream(InstanceScope.values())
                    .filter(candidate -> candidate.getCode().equals(code))
                    .findFirst()
                    .orElseThrow(() -> InstanceException.badProtocol("Unknown instance scope: " + token));
            result.add(scope);
        }
        return result;
    }
}
