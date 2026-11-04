package cn.nitrowater.resource.application.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.resource.application.dto.AdminResourceQuery;
import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceVisibility;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the resource listings.
 *
 * <p>Visibility is part of the filter, not a post-processing step: a paged query must constrain
 * the rows before the page is taken, otherwise the caller sees short or empty pages.</p>
 */
public final class ResourceSpec {

    private ResourceSpec() {
    }

    /**
     * Matches the resources of one app + group that the caller may read: public ones plus the
     * caller's own, all active.
     *
     * @param app application / namespace
     * @param groupKey optional grouping key
     * @param callerUid authenticated caller's uid, or {@code null} for an anonymous caller
     * @param keyword optional original-name substring
     * @return the combined specification
     */
    public static Specification<Resource> of(String app, String groupKey, Long callerUid, String keyword) {
        SpecificationBuilder<Resource> builder = SpecificationBuilder.<Resource>builder()
                .eq("app", app)
                .eq("groupKey", StringUtil.isNotBlank(groupKey) ? groupKey : null)
                .eq("status", ResourceStatus.ACTIVE);
        if (callerUid == null) {
            builder.eq("visibility", ResourceVisibility.PUBLIC);
        } else {
            builder.where((root, query, cb) -> cb.or(
                    cb.equal(root.get("visibility"), ResourceVisibility.PUBLIC),
                    cb.equal(root.get("ownerUid"), callerUid)));
        }
        return builder.likeIgnoreCase("originalName", keyword).build();
    }

    /**
     * Matches one plugin's active artifacts.
     *
     * @param app application / namespace the artifacts were published under
     * @param groupKey plugin key
     * @param keyword optional original-name substring
     * @return the combined specification
     */
    public static Specification<Resource> pluginArtifactsOf(String app, String groupKey, String keyword) {
        return SpecificationBuilder.<Resource>builder()
                .eq("app", app)
                .eq("groupKey", groupKey)
                .eq("status", ResourceStatus.ACTIVE)
                .likeIgnoreCase("originalName", keyword)
                .build();
    }

    /**
     * Matches the admin listing, always constraining by the caller's visibility.
     *
     * <p>Soft-deleted rows are never part of a listing: their bytes are gone, the row only exists
     * so a later purge/audit can still resolve the uuid.</p>
     *
     * @param query filter sent by the admin UI
     * @param callerUid caller's uid, used when the caller is not a super admin
     * @param superAdmin whether the caller may see every owner's rows
     * @return the combined specification
     */
    public static Specification<Resource> adminOf(AdminResourceQuery query, Long callerUid,
            boolean superAdmin) {
        SpecificationBuilder<Resource> builder = SpecificationBuilder.<Resource>builder()
                .eq("ownerUid", superAdmin ? null : callerUid)
                .eq("app", StringUtil.isNotBlank(query.app()) ? query.app() : null)
                .eq("groupKey", StringUtil.isNotBlank(query.groupKey()) ? query.groupKey() : null)
                .eq("ownerUid", query.ownerUid())
                .eq("status", query.status())
                .eq("sourceType", query.sourceType());
        builder.when(query.status() == null,
                (root, q, cb) -> cb.notEqual(root.get("status"), ResourceStatus.DELETED));
        return builder.likeIgnoreCase("originalName", query.keyword()).build();
    }
}
