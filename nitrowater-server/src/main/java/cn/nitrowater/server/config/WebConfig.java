package cn.nitrowater.server.config;

import cn.nitrowater.server.ratelimit.SandboxRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the sandbox rate-limit interceptor on the run endpoints. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SandboxRateLimitInterceptor sandboxRateLimitInterceptor;

    public WebConfig(SandboxRateLimitInterceptor sandboxRateLimitInterceptor) {
        this.sandboxRateLimitInterceptor = sandboxRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sandboxRateLimitInterceptor)
                .addPathPatterns("/api/sandbox/**");
    }
}
