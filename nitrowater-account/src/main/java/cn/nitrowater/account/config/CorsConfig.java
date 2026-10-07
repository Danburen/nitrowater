package cn.nitrowater.account.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS 白名单：支持「同主域子域」与「跨主域」两种来源，带凭证（Cookie）。
 * <p>用 {@code allowedOriginPatterns}（支持 {@code https://*.nitrowater.cn}、{@code http://localhost:*}）
 * 且 {@code allowCredentials=true}（不能用 {@code "*"}）。</p>
 * <p>{@link CorsConfigurationSource} bean 同时供 Phase 2 的 Spring Security {@code .cors()} 复用。</p>
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private List<String> allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOriginPatterns);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        FilterRegistrationBean<CorsFilter> reg =
                new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
