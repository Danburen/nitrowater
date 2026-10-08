package cn.nitrowater.bff.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Session introspection for the SPA. The browser never sees a token; it asks the BFF who it is.
 */
@RestController
@RequestMapping("/bff")
public class SessionController {

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> claims = user.getClaims();
        Set<String> roles = new LinkedHashSet<>();
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> list) {
            for (Object role : list) {
                if (role != null) {
                    roles.add(String.valueOf(role));
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("uid", asString(claims.get("uid")));
        body.put("username", asString(claims.get("preferred_username")));
        body.put("name", asString(claims.get("name")));
        body.put("roles", new ArrayList<>(roles));
        body.put("did", asString(claims.get("did")));
        body.put("issuer", asString(claims.get("iss")));
        // exp is not always present in the userinfo-backed claims; fall back to the ID token.
        Long expiresAt = null;
        Object exp = claims.get("exp");
        if (exp instanceof Number number) {
            expiresAt = number.longValue();
        } else if (user.getIdToken() != null && user.getIdToken().getExpiresAt() != null) {
            expiresAt = user.getIdToken().getExpiresAt().getEpochSecond();
        }
        body.put("expiresAt", expiresAt);
        return body;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
