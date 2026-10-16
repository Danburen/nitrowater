package cn.nitrowater.resource.application.dto;

import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.SourceType;

/**
 * Filter for the admin resource listing. Every field is optional; a {@code null} means "do not
 * constrain on this dimension".
 *
 * @param app application / namespace to narrow to, or {@code null}
 * @param groupKey grouping key to narrow to, or {@code null}
 * @param ownerUid owner uid to narrow to, or {@code null}
 * @param status lifecycle status to narrow to, or {@code null} (all statuses)
 * @param sourceType how the resource came to exist, or {@code null}
 * @param keyword case-insensitive substring matched against the original file name, or {@code null}
 */
public record AdminResourceQuery(
        String app,
        String groupKey,
        Long ownerUid,
        ResourceStatus status,
        SourceType sourceType,
        String keyword) {
}
