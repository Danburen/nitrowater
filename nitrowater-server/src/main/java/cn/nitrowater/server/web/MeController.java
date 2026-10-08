package cn.nitrowater.server.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo business endpoints for the Resource Server.
 * <ul>
 *   <li>{@code GET /api/public/ping} — open reachability probe;</li>
 *   <li>{@code GET /api/me} — protected; echoes the identity from the validated SSO access token.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class MeController {

    @GetMapping("/public/ping")
    public Map<String, Object> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pong", true);
        body.put("service", "nitrowater-server");
        return body;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", jwt.getSubject());
        body.put("uid", jwt.getClaimAsString("uid"));
        body.put("preferred_username", jwt.getClaimAsString("preferred_username"));
        body.put("name", jwt.getClaimAsString("name"));
        body.put("roles", jwt.getClaimAsStringList("roles"));
        body.put("did", jwt.getClaimAsString("did"));
        body.put("iss", jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
        body.put("aud", jwt.getAudience());
        return body;
    }
}
