package cn.nitrowater.server.web.dto;

import java.time.Instant;

/**
 * Editorial list item (no body).
 *
 * <p>All {@code PUBLISHED} editorials are publicly visible; {@code DRAFT}/{@code HIDDEN} are
 * admin-only.</p>
 *
 * @param id editorial id
 * @param problemId owning problem id
 * @param authorType {@code OFFICIAL}/{@code USER}
 * @param title title
 * @param language code-snippet language id, e.g. {@code java} (nullable)
 * @param status {@code DRAFT}/{@code PUBLISHED}/{@code HIDDEN}
 * @param pinned whether pinned
 * @param likeCount like count
 * @param viewCount view count
 * @param publishedAt publish time (nullable)
 * @param createdAt creation time
 */
public record EditorialSummaryResp(
        Long id,
        Long problemId,
        String authorType,
        String title,
        String language,
        String status,
        Boolean pinned,
        Integer likeCount,
        Integer viewCount,
        Instant publishedAt,
        Instant createdAt) {
}
