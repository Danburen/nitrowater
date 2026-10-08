package cn.nitrowater.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
public class SecurityConfig {

    private final String issuerUri;
    private final String jwkSetUri;
    private final List<String> acceptedAudiences;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.jwt.audiences}") List<String> acceptedAudiences) {
        this.issuerUri = issuerUri;
        this.jwkSetUri = jwkSetUri;
        this.acceptedAudiences = acceptedAudiences;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/public/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Verifies the signature against the Authorization Server's JWKS and enforces
     * {@code iss} / {@code exp} / {@code nbf} plus this project's audience restriction.
     *
     * <p>Declaring this bean makes Spring Boot's auto-configured {@link JwtDecoder}
     * ({@code @ConditionalOnMissingBean}) step aside, so exactly one decoder exists.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(acceptedAudiences));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /**
     * Maps the SSO access token to authorities: {@code roles} (already {@code ROLE_}-prefixed by
     * {@code SsoUserPrincipal#getRoles()}) and {@code scope} (kept as {@code SCOPE_*} so protocol
     * scopes behave exactly as they do under Spring's default converter).
     *
     * <p>The principal name stays Spring's default {@code sub}, which the Authorization Server
     * sets to the numeric {@code uid}.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> scopes = scopesOf(jwt);
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.size() + 4);
            for (String scope : scopes) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    if (role != null && !role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(role));
                    }
                }
            }
            return new ArrayList<>(authorities);
        });
        return converter;
    }

    /**
     * Reads {@code scope} (or its {@code scp} shorthand) whether the Authorization Server emitted
     * it as a space-delimited string or as a JSON array — Spring's own converter only reliably
     * handles one of the two shapes, and this project's tokens carry an array.
     */
    private static Collection<String> scopesOf(Jwt jwt) {
        Object scope = jwt.getClaim("scope");
        if (scope == null) {
            scope = jwt.getClaim("scp");
        }
        if (scope instanceof Collection<?> values) {
            List<String> result = new ArrayList<>(values.size());
            for (Object value : values) {
                if (value != null) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        if (scope instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
        }
        return List.of();
    }

    /** CORS for the SPA (dev) and the nitrowater.cn sites. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:*", "http://127.0.0.1:*",
                "https://nitrowater.cn", "https://*.nitrowater.cn"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
