package cn.nitrowater.resource.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for {@link Resource} metadata rows. */
public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    /**
     * @param uuid opaque resource identifier
     * @return the resource, or empty when none matches
     */
    Optional<Resource> findByUuid(String uuid);

    /**
     * Lists the resources of one app + group (e.g. a plugin's published downloads), newest first.
     *
     * @param app application / namespace
     * @param groupKey grouping key within the app (e.g. plugin key)
     * @param status lifecycle status to include (e.g. {@code ACTIVE})
     * @return matching resources, newest first
     */
    List<Resource> findByAppAndGroupKeyAndStatusOrderByCreatedAtDesc(
            String app, String groupKey, ResourceStatus status);

    /**
     * Finds an existing system resource with the same app + group + original name, so a re-publish of
     * the same file updates the row in place instead of creating a duplicate.
     *
     * @param app application / namespace
     * @param groupKey grouping key within the app (e.g. plugin key)
     * @param originalName original file name
     * @param sourceType how the resource came to exist (e.g. {@code SYSTEM})
     * @return the newest matching resource, or empty when none matches
     */
    Optional<Resource> findFirstByAppAndGroupKeyAndOriginalNameAndSourceTypeOrderByCreatedAtDesc(
            String app, String groupKey, String originalName, SourceType sourceType);

    /**
     * Finds stale pending rows for the orphan reaper. A pending row is created before a client
     * uploads its bytes; if the upload is abandoned it would otherwise stay pending forever. The
     * batch cap keeps one cleanup run bounded.
     *
     * @param status lifecycle status to match (normally {@code PENDING})
     * @param cutoff rows updated before this instant are considered abandoned
     * @return up to 200 matching rows, oldest update first
     */
    List<Resource> findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ResourceStatus status, Instant cutoff);

    /**
     * Lists every distinct app namespace in the table, for the console's app filter. A super-admin
     * sees all namespaces; the caller-scoped variant below covers the other admins.
     *
     * @return distinct app namespaces, ascending
     */
    @Query("select distinct r.app from Resource r order by r.app")
    List<String> findDistinctApps();

    /**
     * Lists the distinct app namespaces among the resources owned by one uid, so a non-super admin's
     * app filter only offers namespaces they can actually see.
     *
     * @param ownerUid the admin's uid
     * @return distinct app namespaces of that owner, ascending
     */
    @Query("select distinct r.app from Resource r where r.ownerUid = :ownerUid order by r.app")
    List<String> findDistinctAppsByOwnerUid(@Param("ownerUid") Long ownerUid);
}
