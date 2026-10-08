package cn.nitrowater.account.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import cn.nitrowater.account.security.SsoUserPrincipal;

import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * OIDC Provider（Spring Authorization Server 7.1）。
 *
 * <p>The SPA no longer talks to this provider directly: the {@code nitrowater-bff} <em>confidential</em>
 * client is the only browser-facing party, and it is the client type the Authorization Server is
 * designed to issue refresh tokens to. The former public {@code nitrowater-web} client (which
 * relied on iframe {@code prompt=none} renewal) has been retired.</p>
 */
@Configuration
public class AuthorizationServerConfig {

    /** Encodes the confidential client secret; SAS verifies with a delegating encoder. */
    private static final PasswordEncoder CLIENT_SECRET_ENCODER =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    /** AS 端点链（/oauth2/**、/.well-known/**、/userinfo 等）。未登录的 HTML 请求跳 /login。 */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, (as) -> as.oidc(Customizer.withDefaults()))
                .cors(c -> c.configurationSource(cors))
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors)
            throws Exception {
        http
                .cors(c -> c.configurationSource(cors))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/register", "/api/auth/**", "/auth/**", "/error", "/favicon.ico")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(l -> l.logoutSuccessUrl("/"));
        return http.build();
    }

    /**
     * JDBC Persistence
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * JDBC authorization store. The default AS mapper denies the custom
     * {@link SsoUserPrincipal} type, so a mapper allowing our package is supplied.
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService service =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        JsonMapper jsonMapper = authorizationJsonMapper();

        JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                        registeredClientRepository, jsonMapper);
        service.setAuthorizationRowMapper(rowMapper);

        JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper parametersMapper =
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper);
        service.setAuthorizationParametersMapper(parametersMapper);
        return service;
    }

    /** Authorization JSON mapper that allows the SSO principal package. */
    static JsonMapper authorizationJsonMapper() {
        ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();
        BasicPolymorphicTypeValidator.Builder validatorBuilder = BasicPolymorphicTypeValidator.builder();
        List<JacksonModule> modules = SecurityJacksonModules.getModules(classLoader, validatorBuilder);
        validatorBuilder.allowIfSubType("cn.nitrowater.account.");
        return JsonMapper.builder().addModules(modules).build();
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * Idempotently seeds the first-party {@code nitrowater-bff} client.
     *
     * <p>The BFF is a <em>confidential</em> client (client_secret + authorization_code +
     * refresh_token). This is what makes the Authorization Server issue a refresh token: it
     * deliberately withholds one from public clients, so the BFF is the supported way to get
     * server-side silent refresh (no iframe).</p>
     */
    @Bean
    public ApplicationRunner registeredClientSeeder(
            RegisteredClientRepository registeredClientRepository,
            @Value("${app.oidc.bff-client-secret}") String bffClientSecret,
            @Value("${app.oidc.bff-redirect-uris}") String bffRedirectUris,
            @Value("${app.oidc.bff-post-logout-redirect-uris}") String bffPostLogoutRedirectUris) {
        return args -> upsertClient(registeredClientRepository, "nitrowater-bff",
                id -> nitrowaterBffClient(id, bffClientSecret, bffRedirectUris, bffPostLogoutRedirectUris));
    }

    /**
     * Insert-or-update by client id: a fresh row gets a new id, an existing row is updated in
     * place so config changes (secret / redirect URIs) reconcile on restart.
     */
    private static void upsertClient(RegisteredClientRepository repository, String clientId,
            Function<String, RegisteredClient> factory) {
        RegisteredClient existing = repository.findByClientId(clientId);
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
        repository.save(factory.apply(id));
    }

    private static RegisteredClient nitrowaterBffClient(String id, String secret,
            String redirectUrisCsv, String postLogoutRedirectUrisCsv) {
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId("nitrowater-bff")
                .clientSecret(CLIENT_SECRET_ENCODER.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        // Confidential client: authenticated by client_secret, so PKCE is not
                        // required. (SAS 7.1 defaults require-proof-key to true — set it explicitly.)
                        .requireProofKey(false)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        // Short AT TTL narrows the revocation window; the BFF refreshes silently
                        // with the refresh token. The resource server is unaffected.
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build());
        for (String uri : splitCsv(redirectUrisCsv)) {
            builder.redirectUri(uri);
        }
        for (String uri : splitCsv(postLogoutRedirectUrisCsv)) {
            builder.postLogoutRedirectUri(uri);
        }
        return builder.build();
    }

    /** Parses a comma separated list, ignoring blanks. */
    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /** JWKS：复用 core RSA 密钥对。 */
    @Bean
    public JWKSource<SecurityContext> jwkSource(PublicKey publicKey, PrivateKey privateKey) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) publicKey)
                .privateKey((RSAPrivateKey) privateKey)
                .keyID("nitrowater-key-1")
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${jwt.issuer:http://localhost:8090}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /**
     * Customizer token
     * @return encoded jwt context
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
            @Value("${app.oidc.access-token-audiences:}") String resourceAudiencesCsv) {
        return context -> {
            SsoUserPrincipal principal = resolvePrincipal(context);
            String uid = principal != null
                    ? String.valueOf(principal.getUid())
                    : resolveUid(context);
            if (uid == null) {
                return;
            }
            var claims = context.getClaims();
            claims.subject(uid);
            claims.claim("uid", uid);
            if (principal != null) {
                if (principal.getLoginName() != null) {
                    claims.claim("preferred_username", principal.getLoginName());
                }
                claims.claim("name", principal.getDisplayName());
                claims.claim("roles", principal.getRoles());
                if (principal.getDid() != null) {
                    claims.claim("did", principal.getDid());
                }
            }
            // RFC 9068: the access token's `aud` identifies the RESOURCE it may be used on.
            // Id tokens are deliberately untouched — OIDC Core §2 requires their `aud` to be
            // exactly the client_id, so widening it would break OIDC conformance.
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Set<String> distinct = new LinkedHashSet<>(resourceAudiencesOf(resourceAudiencesCsv));
                RegisteredClient client = context.getRegisteredClient();
                if (client != null) {
                    // keep the client identity so existing audience checks keep passing
                    distinct.add(client.getClientId());
                }
                if (!distinct.isEmpty()) {
                    claims.audience(new ArrayList<>(distinct));
                }
            }
        };
    }

    /**
     * Parses {@code app.oidc.access-token-audiences} (comma separated) into a list.
     *
     * <p>Split by hand rather than relying on {@code @Value} list conversion so a YAML list and
     * a comma separated scalar behave the same way.
     */
    private static List<String> resourceAudiencesOf(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * Resolve principle from a jwt context
     * @param context {@link JwtEncodingContext}
     * @return {@link SsoUserPrincipal}
     */
    private static SsoUserPrincipal resolvePrincipal(JwtEncodingContext context) {
        Authentication authentication = context.getPrincipal();
        if (authentication != null && authentication.getPrincipal() instanceof SsoUserPrincipal p) {
            return p;
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            Object stored = authorization.getAttribute(Principal.class.getName());
            if (stored instanceof Authentication a && a.getPrincipal() instanceof SsoUserPrincipal p) {
                return p;
            }
        }
        return null;
    }

    /**
     * Resolve simple uid
     * @param context {@link JwtEncodingContext}
     * @return {@link SsoUserPrincipal}
     */
    private static String resolveUid(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            return authorization.getPrincipalName();
        }
        Authentication principal = context.getPrincipal();
        return principal != null ? principal.getName() : null;
    }
}
