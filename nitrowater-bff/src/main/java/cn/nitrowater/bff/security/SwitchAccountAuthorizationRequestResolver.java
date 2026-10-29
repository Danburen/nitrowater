package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds {@code prompt=login} when the SPA asks to add another account.
 *
 * <p>The BFF starts the flow at {@code /oauth2/authorization/nitrowater?switch=1}. The standard
 * resolver ignores that flag; this decorator turns it into the OIDC {@code prompt=login}
 * parameter, which the Authorization Server uses (via its own prompt filter) to force a fresh
 * credential prompt instead of silently reusing the current SSO session. That is what makes
 * "add another account" possible while the first account is still logged in.</p>
 */
public class SwitchAccountAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** Query parameter the SPA uses to signal "add another account". */
    public static final String SWITCH_PARAMETER = "switch";

    private static final String PROMPT_PARAMETER = "prompt";

    private final OAuth2AuthorizationRequestResolver delegate;

    public SwitchAccountAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return customize(delegate.resolve(request, registrationId), request);
    }

    private static OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest request,
            HttpServletRequest servletRequest) {
        if (request == null || !"1".equals(servletRequest.getParameter(SWITCH_PARAMETER))) {
            return request;
        }
        Map<String, Object> additional = new LinkedHashMap<>(request.getAdditionalParameters());
        additional.put(PROMPT_PARAMETER, "login");

        // The standard resolver has already expanded the query into `authorizationRequestUri`;
        // changing only `additionalParameters` would leave the redirect URL untouched.
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(request)
                .additionalParameters(additional);
        String uri = request.getAuthorizationRequestUri();
        if (uri != null && !uri.isBlank()) {
            builder.authorizationRequestUri(UriComponentsBuilder.fromUriString(uri)
                    .replaceQueryParam(PROMPT_PARAMETER, "login")
                    .build(true)
                    .toUriString());
        }
        return builder.build();
    }
}
