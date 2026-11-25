package cn.nitrowater.server.service.sponsor;

/**
 * The only sponsor fields the About page needs. Nothing else from the ifdian response is cached.
 *
 * @param userId ifdian user id, used as a stable key for the card
 * @param nickname sponsor display name
 * @param avatarUrl sponsor avatar URL, empty when ifdian has none
 * @param lastSponsorTime last sponsorship time, as returned by ifdian
 */
public record SponsorView(
        String userId,
        String nickname,
        String avatarUrl,
        String lastSponsorTime) {
}
