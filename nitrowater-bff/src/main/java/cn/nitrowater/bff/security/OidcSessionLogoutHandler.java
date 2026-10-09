package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cleans up the BFF's server-side tokens during logout and hands the logout success handler a
 * fresh {@code id_token} for RP-Initiated Logout.
 *
 * <p><strong>Why a refresh is required.</strong> The Authorization Server re-mints the
 * {@code id_token} on every access-token refresh and replaces the stored value, so the
 * {@code id_token} captured at login is no longer resolvable by {@code /connect/logout} (the AS
 * would answer {@code 400 invalid_token}). Refreshing here yields the <em>current</em>
 * {@code id_token}; because the BFF uses non-reused refresh tokens it also rotates the refresh
 * token.</p>
 *
 * <p><strong>Cleanup.</strong> The rotated refresh token is revoked at the Authorization Server's
 * RFC 7009 endpoint, so no usable refresh token survives the logout. The access token is a
 * self-contained JWT that the resource server validates offline against JWKS, so revoking it has
 * no effect there and is intentionally skipped (it expires on its own, short TTL).</p>
 *
 * <p>This handler runs while the session is still alive (before the default
 * {@code SecurityContextLogoutHandler} invalidates it). Every upstream call is best-effort: a
 * failure is logged and logout still proceeds locally.</p>
 */
public class OidcSessionLogoutHandler implements LogoutHandler {

    /** Request attribute carrying the freshly minted {@code id_token} for the success handler. */
    public static final String FRESH_ID_TOKEN_ATTRIBUTE =
            OidcSessionLogoutHandler.class.getName() + ".FRESH_ID_TOKEN";

    private static final Logger log = LoggerFactory.getLogger(OidcSessionLogoutHandler.class);

    private static final String REVOCATION_ENDPOINT_CLAIM = "revocation_endpoint";
    private static final String DEFAULT_REVOCATION_PATH = "/oauth2/revoke";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String registrationId;

    public OidcSessionLogoutHandler(ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            String registrationId) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizedClientRepository = authorizedClientRepository;
        this.registrationId = registrationId;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        ClientRegistration registration =
                this.clientRegistrationRepository.findByRegistrationId(this.registrationId);
        if (registration == null) {
            return;
        }
        OAuth2AuthorizedClient authorizedClient = this.authorizedClientRepository
                .loadAuthorizedClient(this.registrationId, authentication, request);
        if (authorizedClient == null || authorizedClient.getRefreshToken() == null) {
            log.debug("Logout: no server-side refresh token in session; nothing to revoke");
            return;
        }

        String refreshToken = authorizedClient.getRefreshToken().getTokenValue();
        try {
            JsonNode refreshed = refreshTokens(registration, refreshToken);
            String idToken = refreshed.path("id_token").asString();
            if (idToken != null && !idToken.isBlank()) {
                request.setAttribute(FRESH_ID_TOKEN_ATTRIBUTE, idToken);
            }
            String rotated = refreshed.path("refresh_token").asString();
            revokeRefreshToken(registration, rotated != null && !rotated.isBlank() ? rotated : refreshToken);
        } catch (Exception ex) {
            log.warn("Logout: upstream token cleanup failed, continuing local logout: {}", ex.toString());
        }
    }

    /** Exchanges the refresh token for a fresh token set (which carries a current id_token). */
    private JsonNode refreshTokens(ClientRegistration registration, String refreshToken) throws Exception {
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
        return this.jsonMapper.readTree(body);
    }

    /** Revokes the refresh token at the Authorization Server (RFC 7009). */
    private void revokeRefreshToken(ClientRegistration registration, String refreshToken) {
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
            log.warn("Logout: refresh token revocation failed: {}", ex.toString());
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
