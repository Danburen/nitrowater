package cn.nitrowater.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the SPA build output, the history-mode fallback, and the independently-deployed
 * plugin-docs sub-sites.
 *
 * <ul>
 *   <li>{@code /plugin/**} — every plugin's documentation (a statically-built VitePress site) is
 *       mounted under the {@code /plugin/<name>/} namespace (e.g. {@code /plugin/velochatx/}) and
 *       served straight from the deploy-owned directory {@code app.web.plugin-docs-dir}, one
 *       sub-directory per plugin. The docs are NOT part of the web build: the docs repo's own
 *       pipeline publishes versions there and the portal only links {@code /plugin/velochatx/}.
 *       Registered before the catch-all so it is never swallowed by the SPA fallback. The handler
 *       maps the whole docs root generically, so publishing a brand-new plugin needs no restart
 *       (files are read per request).</li>
 *   <li>{@code /assets/**} — Vite emits content-hashed filenames, so these bundles are immutable
 *       and cached for {@code app.web.assets-cache-seconds} (default 1 year).</li>
 *   <li>{@code /**} — everything else (index.html and client-side routes) with a history fallback
 *       that resolves unknown extensionless routes to {@code index.html}; never long-cached so a
 *       new deploy is picked up immediately.</li>
 * </ul>
 *
 * <p>Boot's default static mappings are disabled in {@code application.yml}
 * ({@code spring.web.resources.add-mappings: false}) so these handlers are authoritative.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.web.assets-cache-seconds:31536000}")
    private long assetsCacheSeconds;

    /** Root of the deploy-owned plugin-docs directory (one sub-directory per plugin). */
    @Value("${app.web.plugin-docs-dir:./deploy/plugin-docs}")
    private String pluginDocsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Plugin docs: each plugin's independently built & deployed static site lives in its own
        // sub-directory of the deploy-owned docs root and is served under the /plugin/<name>/
        // namespace (updated out-of-band by the docs repo's pipeline — no web build, no restart).
        // Must be registered BEFORE the catch-all below so it wins.
        registry.addResourceHandler("/plugin/**")
                .addResourceLocations("file:" + trimTrailingSlash(pluginDocsDir) + "/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // The resolved path may arrive with a leading "/" (e.g. "/" for the base
                        // dir); createRelative would then resolve against the FS root, so strip it.
                        String rel = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
                        if (rel.isEmpty()) {
                            rel = "index.html";
                        }
                        // A real file wins. NB: Spring strips the trailing slash before handing the
                        // path over, so a directory request arrives WITHOUT one — detect it by the
                        // resource simply not being a readable file, then fall back to its index.html.
                        Resource requested = location.createRelative(rel);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        Resource index = location.createRelative(rel + "/index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }
                });

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

    /** @return {@code value} without a trailing slash, so path concatenation never doubles it */
    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
