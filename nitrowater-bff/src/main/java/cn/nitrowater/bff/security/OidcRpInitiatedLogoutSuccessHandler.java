package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Implements RP-Initiated Logout: after the BFF destroys its own session it redirects the
 * <em>browser</em> (a top-level navigation, so the AS session cookie is sent) to the
 * Authorization Server's {@code end_session_endpoint} with {@code id_token_hint} and
 * {@code post_logout_redirect_uri}. The AS then invalidates the SSO session and redirects back
 * to the BFF.
 *
 * <p>The {@code id_token_hint} prefers the freshly minted token stashed by
 * {@link OidcSessionLogoutHandler} (which keeps it resolvable by the AS after refreshes), and
 * falls back to the session's {@code id_token}. If neither the token nor the endpoint can be
 * resolved the handler degrades to a local-only logout (redirect to {@code fallbackUri}).</p>
 */
@Slf4j
public class OidcRpInitiatedLogoutSuccessHandler implements LogoutSuccessHandler {
    private static final String END_SESSION_ENDPOINT_CLAIM = "end_session_endpoint";
    private static final String DEFAULT_END_SESSION_PATH = "/connect/logout";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final String registrationId;
    private final String fallbackUri;
    /** Absolute origin the AS must send the browser back to; blank keeps the request origin. */
    private final String postLogoutRedirectUri;

    public OidcRpInitiatedLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository,
            String registrationId) {
        this(clientRegistrationRepository, registrationId, "/", null);
    }

    public OidcRpInitiatedLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository,
            String registrationId, String fallbackUri, String postLogoutRedirectUri) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.registrationId = registrationId;
        this.fallbackUri = fallbackUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        String idToken = resolveIdToken(request, authentication);
        ClientRegistration registration =
                this.clientRegistrationRepository.findByRegistrationId(this.registrationId);
        if (registration == null) return;
        String endSessionEndpoint = endSessionEndpoint(registration);

        if (idToken == null || endSessionEndpoint == null) {
            log.debug("Logout: no id_token or end_session_endpoint; performing local-only logout");
            response.sendRedirect(this.fallbackUri);
            return;
        }

        String separator = endSessionEndpoint.contains("?") ? "&" : "?";
        String logoutUrl = endSessionEndpoint
                + separator
                + "id_token_hint=" + urlEncode(idToken)
                + "&post_logout_redirect_uri=" + urlEncode(postLogoutRedirect(request));
        response.sendRedirect(logoutUrl);
    }


    /**
     * The origin the AS sends the browser back to.
     *
     * <p>It MUST be one of the client's registered {@code post_logout_redirect_uris}: the AS
     * answers {@code 400 invalid_request} for anything else, which shows up as a blank page AND
     * (because validation runs before session invalidation) leaves the SSO session alive, so the
     * next "login" silently re-authenticates the previous user. Deriving it from the request Host
     * broke whenever the SPA was opened through a host the account service does not list
     * (127.0.0.1 vs localhost, a different port, a LAN address), so the configured value wins.
     *</p>
     *
     * @param request current request, used only when nothing is configured
     * @return an absolute http(s) URI registered as a post-logout redirect
     */
    private String postLogoutRedirect(HttpServletRequest request) {
        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            return postLogoutRedirectUri.trim();
        }
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    /** Prefers the freshly minted id_token, falling back to the session principal's id_token. */
    private static String resolveIdToken(HttpServletRequest request, Authentication authentication) {
        Object fresh = request.getAttribute(OidcSessionLogoutHandler.FRESH_ID_TOKEN_ATTRIBUTE);
        if (fresh instanceof String value && !value.isBlank()) {
            return value;
        }
        if (authentication != null
                && authentication.getPrincipal() instanceof OidcUser oidcUser
                && oidcUser.getIdToken() != null) {
            return oidcUser.getIdToken().getTokenValue();
        }
        return null;
    }

    /** Resolves the end-session endpoint from the AS metadata, falling back to the issuer path. */
    private static String endSessionEndpoint(ClientRegistration registration) {
        if (registration == null) {
            return null;
        }
        Object endpoint = registration.getProviderDetails().getConfigurationMetadata()
                .get(END_SESSION_ENDPOINT_CLAIM);
        if (endpoint != null) {
            return endpoint.toString();
        }
        String issuer = registration.getProviderDetails().getIssuerUri();
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        return (issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer)
                + DEFAULT_END_SESSION_PATH;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
