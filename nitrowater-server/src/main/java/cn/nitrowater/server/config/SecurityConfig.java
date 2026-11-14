package cn.nitrowater.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import cn.nitrowater.core.infrastructure.security.ResourceServerJwtConfig;
import cn.nitrowater.core.web.ApiResponseAccessDeniedHandler;
import cn.nitrowater.core.web.ApiResponseAuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * OAuth2 Resource Server: the business API validates the SSO-issued JWT (Bearer) on every
 * {@code /api/**} request; {@code /api/public/**} and {@code /error} stay open. Stateless.
 *
 * <p>Two validations are wired explicitly instead of relying on {@code Customizer.withDefaults()}:
 * <ul>
 *   <li><b>audience</b> — Spring does not check {@code aud} by default, so a token issued for
 *       another client would otherwise be accepted;</li>
 *   <li><b>authorities</b> — Spring's default converter only maps {@code scope} / {@code scp},
 *       while this project's authorization model lives in the {@code roles} claim.</li>
 * </ul>
 *
 * <p>Both remain fully stateless: the JWKS public key is cached in memory and no request ever
 * touches MySQL or Redis on the hot path.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(ResourceServerJwtConfig.class)
public class SecurityConfig {

    /** The decoder and the authorities converter come from core (shared by every downstream service). */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new ApiResponseAuthenticationEntryPoint())
                        .accessDeniedHandler(new ApiResponseAccessDeniedHandler()))
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/public/**", "/docs/**", "/error").permitAll()
                        // Every admin surface lives under /api/admin/**; enforcing the role here
                        // means a new admin controller cannot be exposed by forgetting @PreAuthorize
                        // (method security stays as defence in depth).
                        .requestMatchers("/api/admin/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /** CORS for the SPA and the public sites; override with CORS_ALLOWED_ORIGIN_PATTERNS. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors-allowed-origin-patterns:"
                    + "http://localhost:*,http://127.0.0.1:*,https://nitrowater.cn,https://*.nitrowater.cn}")
            List<String> allowedOriginPatterns) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOriginPatterns);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        // Bearer-token API: the browser never attaches cookies here, so credentialed CORS is
        // unnecessary and only widens exposure (a localhost/subdomain page could otherwise read
        // authenticated responses). Kept off deliberately.
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}