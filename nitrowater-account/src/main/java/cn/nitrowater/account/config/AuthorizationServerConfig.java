package cn.nitrowater.account.config;

import cn.nitrowater.lib.utils.StringUtil;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
import cn.nitrowater.core.web.ApiResponseAccessDeniedHandler;
import cn.nitrowater.core.web.ApiResponseAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import cn.nitrowater.account.security.PromptLoginFilter;
import cn.nitrowater.account.security.SsoUserPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    /**
     * Client id of the first-party BFF (the only browser-facing client).
     *
     * <p>Exposed so the admin console can protect this client from deletion
     * ({@code RegisteredClientAdminService}).</p>
     */
    public static final String BFF_CLIENT_ID = "nitrowater-bff";

    /**
     * Display name of the BFF client, shown on the hosted login page while this app is logging
     * in ("you are signing in to ..."). Editable afterwards in the admin console.
     */
    public static final String BFF_CLIENT_NAME = "NitroWater 门户";

    /** Encodes the confidential client secret; SAS verifies with a delegating encoder. */
    private static final PasswordEncoder CLIENT_SECRET_ENCODER =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    /**
     * Paths reachable without a session and exempt from CSRF — hosted auth pages plus the public
     * auth API. One array so the two lists cannot drift apart.
     */
    private static final String[] UNPROTECTED_PATHS = {
            "/login", "/register", "/api/auth/**", "/auth/**", "/error",
            "/favicon.ico", "/favicon.svg", "/admin/favicon.svg"};

    /** AS 端点链（/oauth2/**、/.well-known/**、/userinfo 等）。未登录的 HTML 请求跳 /login。 */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
            RequestCache requestCache,
            SessionRegistry sessionRegistry)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, (as) -> as.oidc(Customizer.withDefaults()))
                .requestCache(c -> c.requestCache(requestCache))
                .cors(c -> c.configurationSource(cors))
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()))
                // A revoked (expired) SSO session must not be able to walk /oauth2/authorize and
                // mint fresh tokens, so the same expiry check guards the OIDC endpoints.
                .addFilterBefore(expiredSessionFilter(sessionRegistry), ConcurrentSessionFilter.class)
                // SAS 7.1 accepts but ignores prompt=login/select_account; enforce it ourselves so
                // the BFF can add a second account without reusing the current SSO session.
                // `OAuth2AuthorizationEndpointFilter` has no registered order, so anchor after the
                // security-context loader instead: this runs early enough and still has the
                // authenticated principal available.
                .addFilterAfter(new PromptLoginFilter(requestCache, sessionRegistry),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
            RequestCache requestCache,
            SessionRegistry sessionRegistry,
            SsoLoginSuccessHandler ssoLoginSuccessHandler,
            SsoLoginFailureHandler ssoLoginFailureHandler,
            CspPolicy cspPolicy,
            @Qualifier("accountApiJwtDecoder") JwtDecoder accountApiJwtDecoder)
            throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .requestCache(c -> c.requestCache(requestCache))
                .cors(c -> c.configurationSource(cors))
                // Static string, built once: the cost is one header write per response.
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(cspPolicy.directives()))
                        .referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(f -> f.deny()))
                // Cookie-authenticated writes must echo XSRF-TOKEN; see UNPROTECTED_PATHS for the
                // same-origin auth pages that stay exempt.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(UNPROTECTED_PATHS))
                .authorizeHttpRequests(a -> a
                        // The public auth API (captcha / send-code / register / login-by-code) is
                        // anonymous, but the self-service account center is NOT: keep the two apart so
                        // "unauthenticated" can never read or mutate someone's account data. Both
                        // prefixes share UNPROTECTED_PATHS for CSRF, which is a separate concern.
                        .requestMatchers("/api/auth/account", "/api/auth/account/**")
                        .authenticated()
                        .requestMatchers(UNPROTECTED_PATHS)
                        .permitAll()
                        // Admin SPA shell: requiring a session makes an anonymous hit 302 to /login
                        // with a saved request, so login returns to /admin/.
                        .requestMatchers("/admin", "/admin/**")
                        .authenticated()
                        // Role / permission management is reserved for the super admin (站长).
                        .requestMatchers("/api/admin/**")
                        .hasAuthority("ROLE_SUPER_ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(ssoLoginSuccessHandler)
                        .failureHandler(ssoLoginFailureHandler))
                // Account APIs accept either the browser SSO session or the BFF-held Bearer token.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(accountApiJwtDecoder)))
                // Registry tracks sessions (no concurrency limit) so a credential/role change can
                // terminate them all — see SessionInvalidationListener.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry))
                // Setting maximumSessions already makes Spring build a ConcurrentSessionFilter, but
                // that one answers with a plain-text body; ours runs first and returns 401 JSON.
                .addFilterBefore(expiredSessionFilter(sessionRegistry), ConcurrentSessionFilter.class)
                // Straight to the login page: going via "/" would seed a saved request.
                .logout(l -> l.logoutSuccessUrl("/login"))
                .exceptionHandling(ex -> ex
                        // XHR under /api/** gets a clean 401 JSON instead of a 302 to the HTML login page.
                        .defaultAuthenticationEntryPointFor(
                                new ApiResponseAuthenticationEntryPoint(),
                                request -> request.getServletPath().startsWith("/api/"))
                        .accessDeniedHandler(new ApiResponseAccessDeniedHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Request cache backing the form-login "saved request" redirect (and therefore the
     * {@code ?continue} resume the browser performs after login).
     *
     * <p>Only <em>top-level browser navigations</em> are cached — i.e. document requests whose
     * {@code Accept} header asks for {@code text/html}. Sub-resources and background probes are not
     * navigations, yet while anonymous they still run through {@code ExceptionTranslationFilter},
     * which caches them as the pending request. Because the cache stores a single saved request,
     * such a probe would OVERWRITE the pending OIDC {@code /oauth2/authorize} request: the success
     * handler would then send the browser to the probe after login instead of resuming the
     * authorization, leaving the user stuck on a {@code ...?continue} URL. A real offender is
     * Chrome DevTools' automatic {@code /.well-known/appspecific/com.chrome.devtools.json} fetch
     * (the login page's own favicon is now served publicly at {@code /favicon.svg}).</p>
     *
     * <p>This is required because the default {@link HttpSessionRequestCache} caches
     * <em>every</em> unauthenticated request (its {@code matchingRequestParameterName} is the
     * {@code continue} query the client sees).</p>
     */
    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> {
            String accept = request.getHeader(HttpHeaders.ACCEPT);
            return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
        });
        return requestCache;
    }

    /**
     * Session registry populated by the default chain's {@code maximumSessions(-1)} (tracking only,
     * no concurrency limit). {@link cn.nitrowater.account.security.SessionInvalidationListener}
     * uses it to expire every session of an account whose credentials or roles changed.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Turns an expired {@link org.springframework.security.core.session.SessionInformation} into a
     * 401 JSON rejection. Registered ahead of the plain-text filter Spring builds for
     * {@code maximumSessions}, on both chains.
     */
    private static ConcurrentSessionFilter expiredSessionFilter(SessionRegistry sessionRegistry) {
        return new ConcurrentSessionFilter(sessionRegistry, event -> {
            HttpServletResponse response = event.getResponse();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"unauthorized\",\"data\":null}");
        });
    }

    /** Republishes container session events so dead sessions leave the registry. Static so the
     * listener is registered before the Servlet context finishes initialising. */
    @Bean
    public static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Forces the deferred {@link CsrfToken} to be resolved so the readable {@code XSRF-TOKEN} cookie
     * is written on the response; the admin SPA echoes it back in {@code X-XSRF-TOKEN}.
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
        return args -> upsertClient(registeredClientRepository, BFF_CLIENT_ID,
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
                .clientId(BFF_CLIENT_ID)
                .clientName(BFF_CLIENT_NAME)
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
        if (StringUtil.isBlank(csv)) {
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
    @Primary
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Decoder for the account-management API, which the BFF calls with the user's access token.
     *
     * <p>Unlike the Authorization Server's internal decoder, this one also enforces issuer and
     * audience, so a token minted for an unrelated client cannot read or mutate account data.</p>
     */
    @Bean("accountApiJwtDecoder")
    public JwtDecoder accountApiJwtDecoder(
            @Value("${jwt.issuer:http://localhost:8090}") String issuer,
            @Value("${app.oidc.account-api-audiences:nitrowater-bff}") List<String> audiences) {
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(base + "/oauth2/jwks").build();
        OAuth2TokenValidator<@NotNull Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AccountAudienceValidator(audiences));
        decoder.setJwtValidator(validator);
        return decoder;
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
            @Value("${app.oidc.access-token-audiences:}") String resourceAudiencesCsv,
            @Value("${app.avatar.picture-base-url:}") String pictureBaseUrl) {
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
                // OIDC standard `picture`: a STABLE URL (the resource service read path), derived from
                // the avatar's resource uuid — never a stored/presigned URL, so it cannot expire.
                if (StringUtil.isNotBlank(principal.getAvatarUuid())
                        && !StringUtil.isBlank(pictureBaseUrl)) {
                    claims.claim("picture", trimTrailingSlash(pictureBaseUrl)
                            + "/api/public/resource/" + principal.getAvatarUuid());
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
        if (StringUtil.isBlank(csv)) {
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

    /** @return {@code value} without a trailing slash, so path concatenation never doubles it */
    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
