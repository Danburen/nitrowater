package cn.nitrowater.server.service.sponsor;

import java.util.List;

/**
 * Per-page Redis payload: the projection plus the upstream total, so the controller can hand back a
 * Spring Data {@code Page} without re-fetching every earlier page.
 *
 * @param content sponsors on this page
 * @param total total number of sponsors reported by ifdian
 */
public record SponsorPageCache(List<SponsorView> content, long total) {
}
