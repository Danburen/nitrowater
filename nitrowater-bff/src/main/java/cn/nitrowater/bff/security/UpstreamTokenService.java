package cn.nitrowater.bff.security;

import cn.nitrowater.lib.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Upstream token operations against the Authorization Server.
 *
 * <p>Shared by logout and account removal. Kept separate from the logout handler so both paths
 * rotate, revoke and (when needed) obtain a fresh {@code id_token} with exactly the same rules.</p>
 */
@Slf4j
@Component
public class UpstreamTokenService {

    private static final String REVOCATION_ENDPOINT_CLAIM = "revocation_endpoint";
    private static final String DEFAULT_REVOCATION_PATH = "/oauth2/revoke";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final String registrationId;

    public UpstreamTokenService(ClientRegistrationRepository clientRegistrationRepository,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.registrationId = registrationId;
    }

    public String registrationId() {
        return registrationId;
    }

    /**
     * @return the configured client registration, or {@code null} when it is absent
     */
    public ClientRegistration registration() {
        return clientRegistrationRepository.findByRegistrationId(registrationId);
    }

    /**
     * Exchanges a refresh token for a fresh token set.
     *
     * @param refreshToken current refresh token
     * @return the token endpoint response
     * @throws Exception when the upstream call fails
     */
    public JsonNode refresh(String refreshToken) throws Exception {
        ClientRegistration registration = registration();
        if (registration == null) {
            throw new IllegalStateException("OIDC registration not found: " + registrationId);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        String body = RestClient.create()
                .post()
                .uri(registration.getProviderDetails().getTokenUri())
                .headers(headers -> headers.setBasicAuth(
                        registration.getClientId(), registration.getClientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return JsonUtil.getMapper().readTree(body);
    }

    /**
     * Rotates the refresh token and revokes the rotated value, returning the current
     * {@code id_token} for RP-Initiated Logout.
     *
     * <p>Every failure is logged and swallowed: logout must still complete locally. When the
     * refresh fails the original refresh token is revoked as a best effort.</p>
     *
     * @param refreshToken current refresh token
     * @return a fresh {@code id_token}, or {@code null} when the exchange failed
     */
    public String refreshAndRevoke(String refreshToken) {
        try {
            JsonNode refreshed = refresh(refreshToken);
            String idToken = refreshed.path("id_token").asString();
            String rotated = refreshed.path("refresh_token").asString();
            revoke(rotated != null && !rotated.isBlank() ? rotated : refreshToken);
            return idToken != null && !idToken.isBlank() ? idToken : null;
        } catch (Exception ex) {
            log.warn("Upstream token refresh failed; revoking the original refresh token: {}", ex.toString());
            revoke(refreshToken);
            return null;
        }
    }

    /**
     * Revokes a refresh token at the Authorization Server (RFC 7009). Best effort.
     *
     * @param refreshToken token to revoke
     */
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ClientRegistration registration = registration();
        if (registration == null) {
            return;
        }
        String endpoint = revocationEndpoint(registration);
        if (endpoint == null) {
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            RestClient.create()
                    .post()
                    .uri(endpoint)
                    .headers(headers -> headers.setBasicAuth(
                            registration.getClientId(), registration.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Refresh token revocation failed: {}", ex.toString());
        }
    }

    /** Resolves the revocation endpoint from the AS metadata, falling back to the issuer path. */
    private static String revocationEndpoint(ClientRegistration registration) {
        Object endpoint = registration.getProviderDetails().getConfigurationMetadata()
                .get(REVOCATION_ENDPOINT_CLAIM);
        if (endpoint != null) {
            return endpoint.toString();
        }
        String issuer = registration.getProviderDetails().getIssuerUri();
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        return (issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer)
                + DEFAULT_REVOCATION_PATH;
    }
}
