package cn.nitrowater.account.config;

import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.core.entity.user.AccountStatus;
import cn.nitrowater.core.entity.user.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Phase 2.5: the JDBC authorization store persists the login principal, so the
 * authorization JSON mapper must serialize and deserialize SsoUserPrincipal.
 */
class AuthorizationJsonMapperTest {

    @Test
    void principalRoundTripsThroughAuthorizationMapper() throws Exception {
        JsonMapper mapper = AuthorizationServerConfig.authorizationJsonMapper();

        SsoUserPrincipal principal = new SsoUserPrincipal(
                1647980857L, "smokeuser1", "nick", UserType.COMMON, AccountStatus.ACTIVE,
                "device-fp", "did-value", List.of("ADMIN", "USER"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("java.security.Principal", authentication);

        String json = mapper.writeValueAsString(attributes);
        Map<?, ?> restored = mapper.readValue(json, Map.class);

        Object restoredAuthentication = restored.get("java.security.Principal");
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, restoredAuthentication);

        Object restoredPrincipal = ((UsernamePasswordAuthenticationToken) restoredAuthentication).getPrincipal();
        assertInstanceOf(SsoUserPrincipal.class, restoredPrincipal);
        assertEquals(1647980857L, ((SsoUserPrincipal) restoredPrincipal).getUid());
        assertEquals(List.of("ADMIN", "USER"), ((SsoUserPrincipal) restoredPrincipal).getRoleCodes());
    }

    @Test
    void rolesAreConcreteMutableList() {
        SsoUserPrincipal principal = new SsoUserPrincipal(
                1L, "u", "n", UserType.COMMON, AccountStatus.ACTIVE, null, null, List.of());

        List<String> roles = principal.getRoles();
        assertInstanceOf(ArrayList.class, roles);
        assertEquals(List.of("ROLE_USER"), roles);
        roles.add("ROLE_EXTRA"); // must not throw UnsupportedOperationException
        assertEquals(2, roles.size());
    }

    @Test
    void tokenClaimsWithRolesRoundTripThroughAuthorizationMapper() {
        JsonMapper mapper = AuthorizationServerConfig.authorizationJsonMapper();

        SsoUserPrincipal principal = new SsoUserPrincipal(
                1L, "smokeuser1", "nick", UserType.COMMON, AccountStatus.ACTIVE,
                "fp", "did", List.of("ADMIN", "USER"));

        // Mimics OAuth2Authorization metadata.token.claims (the token customizer output).
        // A non-concrete roles list (List.of / Stream.toList) is rejected on read-back.
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "1");
        claims.put("roles", principal.getRoles());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("metadata.token.claims", claims);

        String json = mapper.writeValueAsString(attributes);
        Map<?, ?> restored = mapper.readValue(json, Map.class);

        Object restoredClaims = restored.get("metadata.token.claims");
        assertInstanceOf(Map.class, restoredClaims);
        assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"), ((Map<?, ?>) restoredClaims).get("roles"));
    }
}
