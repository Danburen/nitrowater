package cn.nitrowater.bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import cn.nitrowater.bff.security.OidcRpInitiatedLogoutSuccessHandler;
import cn.nitrowater.bff.security.OidcSessionLogoutHandler;

import java.io.IOException;

/**
 * BFF security chain.
 *
 * <ul>
 *   <li>OIDC login via the confidential {@code nitrowater} client; the tokens stay server-side
 *       in the Redis-backed session.</li>
 *   <li>The SPA is public (anonymous browsing is allowed); only {@code /api/**} requires a
 *       session, and it answers {@code 401} (not a redirect) so the SPA can react.</li>
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
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) throws Exception {
        // Read the raw (unmasked) token from the cookie and accept it verbatim in the header,
        // which is what a same-origin SPA does; the request attribute name is fixed so the
        // CsrfCookieFilter below can force the deferred token to be written to the cookie.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        // Logout: rotate+revoke the server-held tokens (stashing a fresh id_token), then perform
        // RP-Initiated Logout at the Authorization Server so the SSO session is destroyed too.
        OidcSessionLogoutHandler oidcSessionLogoutHandler = new OidcSessionLogoutHandler(
                clientRegistrationRepository, authorizedClientRepository, registrationId);
        OidcRpInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcRpInitiatedLogoutSuccessHandler(clientRegistrationRepository, registrationId);

        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/", true))
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
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/api/")))
                .logout(logout -> logout
                        .addLogoutHandler(oidcSessionLogoutHandler)
                        .logoutSuccessHandler(oidcLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("BFFSESSION", "XSRF-TOKEN"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
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
