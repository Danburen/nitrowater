package cn.nitrowater.server.web.dto;

import java.time.Instant;

/**
 * Editorial detail (with body).
 *
 * @param id editorial id
 * @param problemId owning problem id
 * @param authorUid author uid (nullable)
 * @param authorType {@code OFFICIAL}/{@code USER}
 * @param title title
 * @param content Chinese markdown body
 * @param language code-snippet language id, e.g. {@code java} (nullable)
 * @param status {@code DRAFT}/{@code PUBLISHED}/{@code HIDDEN}
 * @param pinned whether pinned
 * @param likeCount like count
 * @param viewCount view count
 * @param publishedAt publish time (nullable)
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record EditorialDetailResp(
        Long id,
        Long problemId,
        Long authorUid,
        String authorType,
        String title,
        String content,
        String language,
        String status,
        Boolean pinned,
        Integer likeCount,
        Integer viewCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {
}
