package cn.nitrowater.server.config;

import cn.nitrowater.server.ai.AiRateLimitInterceptor;
import cn.nitrowater.server.infra.ratelimit.SandboxRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the per-user quota interceptors for sandbox runs and assistant chat.
 *
 * <p>There is no documentation static handler anymore: NitroWater docs are a markdown-only corpus
 * and the VeloChatX site is served by the plugin-docs container behind {@code /plugin/**}.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SandboxRateLimitInterceptor sandboxRateLimitInterceptor;
    private final AiRateLimitInterceptor aiRateLimitInterceptor;

    public WebConfig(SandboxRateLimitInterceptor sandboxRateLimitInterceptor,
            AiRateLimitInterceptor aiRateLimitInterceptor) {
        this.sandboxRateLimitInterceptor = sandboxRateLimitInterceptor;
        this.aiRateLimitInterceptor = aiRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sandboxRateLimitInterceptor)
                .addPathPatterns("/api/sandbox/**");
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns("/api/ai/chat", "/api/ai/chat/stream",
                        "/api/ai/agent/chat", "/api/ai/agent/chat/stream");
    }
}
