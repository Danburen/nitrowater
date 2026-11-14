package cn.nitrowater.resource.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import cn.nitrowater.core.infrastructure.security.ResourceServerJwtConfig;
import cn.nitrowater.core.web.ApiResponseAccessDeniedHandler;
import cn.nitrowater.core.web.ApiResponseAuthenticationEntryPoint;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Resource Server: validates the SSO-issued JWT (Bearer) on every {@code /api/**} request
 * except {@code /api/public/**}; stateless.
 *
 * <p>The decoder (JWKS + {@code iss}/{@code exp}/{@code nbf} + audience) and the
 * {@code roles}/{@code scope} → authorities mapping come from core
 * ({@link ResourceServerJwtConfig}), so this class only declares what this service accepts and
 * which URL needs which authority — exactly like the business API.</p>
 *
 * <p>{@code /api/public/**} is anonymous; {@code /internal/**} bypasses JWT (it is not an end-user
 * surface) and is guarded instead by a shared-secret token inside the controller.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(ResourceServerJwtConfig.class)
public class ResourceSecurityConfig {

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
                        .requestMatchers("/api/public/**", "/internal/**", "/error").permitAll()
                        // File-management admin surface: any admin (ROLE_ADMIN) may reach it; the
                        // per-row visibility (own vs. all) and delete rules are enforced in the
                        // service, since they depend on the caller's uid and ROLE_SUPER_ADMIN.
                        .requestMatchers("/api/admin/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        // Uploading content is an administrative operation: only ADMIN (SUPER_ADMIN
                        // implicitly holds it) may create resources. Reading stays public (PUBLIC
                        // rows) / owner-scoped (PRIVATE rows) — the split is per row, not per URL.
                        .requestMatchers("/api/resource/upload/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
