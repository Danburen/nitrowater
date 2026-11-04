package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.server.infra.entity.Plugin;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the plugin catalog listing.
 */
public final class PluginSpec {

    private PluginSpec() {
    }

    /**
     * Matches plugins by enabled flag and keyword.
     *
     * @param enabled optional enabled filter
     * @param keyword optional key / name / tagline / summary substring
     * @return the combined specification
     */
    public static Specification<Plugin> of(Boolean enabled, String keyword) {
        return SpecificationBuilder.<Plugin>builder()
                .eq("enabled", enabled)
                .likeIgnoreCaseAny(keyword, "pluginKey", "name", "tagline", "summary")
                .build();
    }
}
