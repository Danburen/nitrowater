package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Turns the SPA's login hints into OIDC parameters and session state.
 *
 * <p>{@code ?switch=1} becomes {@code prompt=login}: the BFF starts the flow at
 * {@code /oauth2/authorization/nitrowater?switch=1}, and without this the Authorization Server
 * would silently reuse the current SSO session, making a second account impossible to add.</p>
 *
 * <p>{@code ?dfp=<fingerprint>} is stashed in the session for
 * {@link AccountLoginSuccessHandler}. The SPA cannot attach a header to the top-level authorization
 * navigation, so the device fingerprint travels as a query parameter once per login and is hashed
 * into the session there (see {@link SessionGuardFilter}).</p>
 */
public class SwitchAccountAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** Query parameter the SPA uses to signal "add another account". */
    public static final String SWITCH_PARAMETER = "switch";

    /** Query parameter carrying the SPA's device fingerprint into the login flow. */
    public static final String DEVICE_PARAMETER = "dfp";

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
        if (request == null) {
            return null;
        }
        String fingerprint = servletRequest.getParameter(DEVICE_PARAMETER);
        if (SessionGuardFilter.isFingerprint(fingerprint)) {
            servletRequest.getSession(true)
                    .setAttribute(SessionGuardFilter.PENDING_DEVICE_ATTRIBUTE, fingerprint);
        }
        if (!"1".equals(servletRequest.getParameter(SWITCH_PARAMETER))) {
            return request;
        }
        Map<String, Object> additional = new LinkedHashMap<>(request.getAdditionalParameters());
        additional.put(PROMPT_PARAMETER, "login");

        // The standard resolver has already expanded the query into `authorizationRequestUri`;
        // changing only `additionalParameters` would leave the redirect URL untouched.
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(request)
                .additionalParameters(additional);
        String uri = request.getAuthorizationRequestUri();
        if (StringUtil.isNotBlank(uri)) {
            builder.authorizationRequestUri(UriComponentsBuilder.fromUriString(uri)
                    .replaceQueryParam(PROMPT_PARAMETER, "login")
                    .build(true)
                    .toUriString());
        }
        return builder.build();
    }
}
