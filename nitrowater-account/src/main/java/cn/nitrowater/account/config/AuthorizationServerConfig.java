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
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
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
import java.util.List;
import java.util.UUID;

/**
 * Phase 2：OIDC Provider（Spring Authorization Server 7.1）。
 * <p>复用 core 的 RSA 密钥（JwtKeyConfig 提供的 PublicKey/PrivateKey）签发，暴露 JWKS。
 * 认证由 {@link SsoAuthenticationProvider} 委托现有 LoginService。</p>
 */
@Configuration
public class AuthorizationServerConfig {

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

    /** 默认链：Phase 1 的 /api/auth/** 放行；其它需登录；表单登录走自建 /login 页。 */
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
     * Phase 2.5（方案 A）：OIDC 客户端 / 授权 / 同意 全部改为 JDBC 持久化，
     * 使用 Spring Authorization Server 标准表（见 V1_1__oauth2_oidc_role.sql），重启不丢。
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * Phase 2.5: JDBC authorization store. The default AS mapper denies the custom
     * {@link SsoUserPrincipal} type, so a mapper allowing our package is supplied.
     */
    @Bean
    @SuppressWarnings("deprecation") // AS JDBC mapper requires the deprecated LobHandler API
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService service =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        JsonMapper jsonMapper = authorizationJsonMapper();

        JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                        registeredClientRepository, jsonMapper);
        rowMapper.setLobHandler(new DefaultLobHandler());
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

    /** 幂等播种首方客户端 nitrowater-web（已存在则跳过）。 */
    @Bean
    public ApplicationRunner registeredClientSeeder(RegisteredClientRepository registeredClientRepository) {
        return args -> {
            if (registeredClientRepository.findByClientId("nitrowater-web") == null) {
                registeredClientRepository.save(nitrowaterWebClient());
            }
        };
    }

    private static RegisteredClient nitrowaterWebClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("nitrowater-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // 公共客户端 + PKCE
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:5173/auth/callback")
                .postLogoutRedirectUri("http://localhost:5173/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(2))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
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
     * Phase 2.4：令牌定制。
     * <p>把 {@link SsoUserPrincipal} 的身份上下文映射进 access_token / id_token：
     * {@code sub=uid}、{@code uid}、{@code preferred_username}、{@code name}、{@code roles}、{@code did}。
     * 这些 claim 亦经默认 {@code /userinfo}（由 id_token claims 映射）对外暴露。</p>
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
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
        };
    }

    /** 从令牌上下文取回登录主体；AS 在授权码与刷新链路都会注入该 Authentication。 */
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

    /** 回退：取授权记录的 principalName（uid）。 */
    private static String resolveUid(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null && authorization.getPrincipalName() != null) {
            return authorization.getPrincipalName();
        }
        Authentication principal = context.getPrincipal();
        return principal != null ? principal.getName() : null;
    }
}
