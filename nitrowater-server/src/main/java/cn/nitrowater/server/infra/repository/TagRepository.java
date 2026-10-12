package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link Tag}. */
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * @param slug stable tag slug
     * @return the tag with that slug
     */
    Optional<Tag> findBySlug(String slug);

    /**
     * @param slug candidate slug
     * @return whether a tag with that slug already exists
     */
    boolean existsBySlug(String slug);

    /**
     * @return enabled tags ordered by kind then id (stable listing order)
     */
    List<Tag> findByEnabledTrueOrderByKindAscIdAsc();
}
