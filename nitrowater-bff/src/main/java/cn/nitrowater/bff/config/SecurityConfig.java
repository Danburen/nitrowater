package cn.nitrowater.bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.nitrowater.bff.security.AccountLoginSuccessHandler;
import cn.nitrowater.bff.security.JsonAuthenticationEntryPoint;
import cn.nitrowater.bff.security.MultiAccountAuthorizedClientRepository;
import cn.nitrowater.bff.security.OidcRpInitiatedLogoutSuccessHandler;
import cn.nitrowater.bff.security.OidcSessionLogoutHandler;
import cn.nitrowater.bff.security.SessionEpochFilter;
import cn.nitrowater.bff.security.UpstreamTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * BFF security chain.
 *
 * <ul>
 *   <li>OIDC login via the confidential {@code nitrowater} client; the tokens stay server-side
 *       in the Redis-backed session.</li>
 *   <li>The SPA is public (anonymous browsing is allowed); only {@code /api/**} requires a
 *       session, and it answers {@code 401} (not a redirect) so the SPA can react. All data
 *       endpoints MUST live under {@code /api/**}; the reserved {@code /actuator/**} and
 *       {@code /internal/**} prefixes are denied as a fail-closed backstop.</li>
 *   <li>CSRF is mandatory for cookie auth: the token is exposed through the readable
 *       {@code XSRF-TOKEN} cookie so the SPA can echo it in {@code X-XSRF-TOKEN}.</li>
 *   <li>Logout destroys the BFF session (which is what holds the tokens), revokes the refresh
 *       token at the Authorization Server, and then performs RP-Initiated Logout so the upstream
 *       SSO session is destroyed as well. See {@code OidcSessionLogoutHandler} /
 *       {@code OidcRpInitiatedLogoutSuccessHandler}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            MultiAccountAuthorizedClientRepository authorizedClientRepository,
            AccountLoginSuccessHandler accountLoginSuccessHandler,
            OAuth2AuthorizationRequestResolver switchAccountAuthorizationRequestResolver,
            UpstreamTokenService upstreamTokenService,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId,
            @Value("${app.session.epoch:dev}") String sessionEpoch,
            @Value("${app.post-logout-redirect-uri:}") String postLogoutRedirectUri) throws Exception {
        // Read the raw (unmasked) token from the cookie and accept it verbatim in the header,
        // which is what a same-origin SPA does; the request attribute name is fixed so the
        // CsrfCookieFilter below can force the deferred token to be written to the cookie.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        // Logout: rotate+revoke the server-held tokens (stashing a fresh id_token), then perform
        // RP-Initiated Logout at the Authorization Server so the SSO session is destroyed too.
        OidcSessionLogoutHandler oidcSessionLogoutHandler = new OidcSessionLogoutHandler(
                authorizedClientRepository, upstreamTokenService);
        OidcRpInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcRpInitiatedLogoutSuccessHandler(clientRegistrationRepository, registrationId, "/",
                        postLogoutRedirectUri);

        http
                .authorizeHttpRequests(a -> a
                        // Anonymous tool endpoints (e.g. the code sandbox runner) are public here;
                        // abuse is bounded by rate limiting on the resource server.
                        .requestMatchers("/api/public/**").permitAll()
                        // Defence-in-depth backstop: reserved sensitive namespaces are never public,
                        // even if a handler is added under them later. Actuator is not on the classpath
                        // today; this blocks the prefix structurally so a future dependency cannot
                        // silently expose it through the catch-all below. (If a probe endpoint must
                        // ever be public, permit it explicitly here, above the catch-all.)
                        .requestMatchers("/actuator/**", "/internal/**").denyAll()
                        // Convention: ALL data endpoints live under /api/** (gated above). Everything
                        // else is the public SPA surface (index.html, hashed /assets bundles, history-mode
                        // deep links) plus OIDC plumbing (/oauth2/**, /login/**, /logout, /error) and the
                        // anonymous, self-scoped /bff/me.
                        // Account pool: /add starts a fresh authorization and is public because it
                        // only redirects; the rest expose or mutate server-side token state.
                        .requestMatchers("/bff/accounts/add").permitAll()
                        .requestMatchers("/bff/accounts/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(accountLoginSuccessHandler)
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(switchAccountAuthorizationRequestResolver)))
                .headers(headers -> headers
                        // Let the resource handlers own Cache-Control for static files (hashed
                        // assets long-cache, index.html no-cache). Spring Security's default
                        // global "no-store" would otherwise overwrite them at response commit.
                        .cacheControl(cache -> cache.disable())
                        // Dynamic session/API responses must not be cached.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                request -> request.getRequestURI().startsWith("/bff/")
                                        || request.getRequestURI().startsWith("/api/"),
                                new StaticHeadersWriter("Cache-Control", "no-store"))))
                .exceptionHandling(ex ->
                        ex.defaultAuthenticationEntryPointFor(
                                new JsonAuthenticationEntryPoint(),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                )
                .logout(logout -> logout
                        .addLogoutHandler(oidcSessionLogoutHandler)
                        .logoutSuccessHandler(oidcLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("BFFSESSION", "XSRF-TOKEN"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // Drop sessions created by a previous deployment (see app.session.epoch).
                .addFilterAfter(new SessionEpochFilter(sessionEpoch), SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * Forces the deferred {@link CsrfToken} to be resolved so the {@code XSRF-TOKEN} cookie is
     * written on the response; the SPA reads that cookie and sends it back as {@code X-XSRF-TOKEN}.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
