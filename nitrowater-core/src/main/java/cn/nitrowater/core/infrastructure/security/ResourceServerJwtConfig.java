package cn.nitrowater.core.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;

/**
 * The single Resource Server wiring for every downstream service (server / resource).
 *
 * <p>A downstream service only declares <em>what it is</em> — the audiences it accepts — and its
 * URL rules; the mechanics (JWKS signature check, {@code iss}/{@code exp}/{@code nbf}, audience
 * restriction, {@code roles} authority mapping) live here so they cannot drift between services.</p>
 *
 * <p>Activation is explicit ({@code @Import(ResourceServerJwtConfig.class)}) because these modules
 * scan only their own packages, not {@code cn.nitrowater.core}. This class is deliberately <b>not</b>
 * annotated with {@code @Configuration}: the account module component-scans {@code core}, and a
 * scanned second {@code JwtDecoder} would collide with the Authorization Server's own decoder.
 * An imported plain class still has its {@code @Bean} methods processed (Spring's "lite" mode), so
 * only the modules that ask for it get these beans.</p>
 *
 * <p>The Authorization Server module does not import this class either: it validates its own API
 * tokens with a local {@code JWKSource} decoder, and reuses only {@link JwtAuthorities} for the
 * authority mapping.</p>
 */
public class ResourceServerJwtConfig {

    /**
     * Verifies the signature against the Authorization Server's JWKS and enforces
     * {@code iss} / {@code exp} / {@code nbf} plus this service's audience restriction.
     *
     * @param issuerUri expected {@code iss}
     * @param jwkSetUri JWKS endpoint of the Authorization Server
     * @param audiences audiences this service accepts
     * @return the shared decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.jwt.audiences}") List<String> audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(audiences));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /**
     * @return the shared {@code roles}/{@code scope} → authorities mapping
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return JwtAuthorities.converter();
    }
}
