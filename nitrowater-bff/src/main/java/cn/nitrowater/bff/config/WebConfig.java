package cn.nitrowater.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the SPA build output and adds the history-mode fallback.
 *
 * <p>Two handlers keep caching sane for a hashed-bundle SPA:</p>
 * <ul>
 *   <li>{@code /assets/**} — Vite emits content-hashed filenames, so these bundles are immutable
 *       and cached for {@code app.web.assets-cache-seconds} (default 1 year).</li>
 *   <li>{@code /**} — everything else (index.html and client-side routes) with a history fallback
 *       that resolves unknown extensionless routes to {@code index.html}; never long-cached so a
 *       new deploy is picked up immediately.</li>
 * </ul>
 *
 * <p>Boot's default static mappings are disabled in {@code application.yml}
 * ({@code spring.web.resources.add-mappings: false}) so these two handlers are authoritative.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.web.assets-cache-seconds:31536000}")
    private long assetsCacheSeconds;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Content-hashed bundles: safe to cache hard.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("file:./nitrowater-web/dist/assets/", "classpath:/static/assets/")
                .setCachePeriod((int) assetsCacheSeconds);

        // index.html + client routes: revalidate every time, with SPA history fallback.
        registry.addResourceHandler("/**")
                .addResourceLocations("file:./nitrowater-web/dist/", "classpath:/static/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // SPA history fallback: only for extensionless routes, never for assets.
                        if (!resourcePath.contains(".")) {
                            Resource index = location.createRelative("index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                        }
                        return null;
                    }
                });
    }
}
