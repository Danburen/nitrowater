package cn.nitrowater.resource.config;

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
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 Resource Server: validates the SSO-issued JWT (Bearer) on every {@code /api/**} request
 * except {@code /api/public/**}; stateless.
 *
 * <p>Audience is enforced explicitly (Spring does not check {@code aud} by default), and the
 * {@code roles} claim is mapped to authorities the same way as the business API.</p>
 */
@Configuration
@EnableWebSecurity
public class ResourceSecurityConfig {

    private final String issuerUri;
    private final String jwkSetUri;
    private final List<String> acceptedAudiences;

    public ResourceSecurityConfig(
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

    /** Verifies signature (JWKS) + {@code iss}/{@code exp}/{@code nbf} + this service's audience. */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(acceptedAudiences));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /** Maps {@code roles} (already {@code ROLE_}-prefixed) and {@code scope} to authorities. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            for (String scope : scopesOf(jwt)) {
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
}
