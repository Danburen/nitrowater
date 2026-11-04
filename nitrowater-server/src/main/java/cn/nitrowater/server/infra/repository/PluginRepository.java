package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.Plugin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link Plugin}. */
public interface PluginRepository extends JpaRepository<Plugin, Long>, JpaSpecificationExecutor<Plugin> {

    /**
     * @param pluginKey stable plugin key
     * @return the enabled plugin with that key (public detail)
     */
    Optional<Plugin> findByPluginKeyAndEnabledTrue(String pluginKey);

    /**
     * @param pluginKey candidate key
     * @return whether a plugin with that key already exists
     */
    boolean existsByPluginKey(String pluginKey);

    /**
     * @return enabled plugins ordered for the public listing
     */
    List<Plugin> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /**
     * @return every plugin ordered for the admin listing
     */
    List<Plugin> findAllByOrderBySortOrderAscIdAsc();
}
