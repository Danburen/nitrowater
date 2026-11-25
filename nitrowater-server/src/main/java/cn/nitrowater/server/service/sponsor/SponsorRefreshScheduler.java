package cn.nitrowater.server.service.sponsor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proactively warms the first sponsor page so an anonymous visit to the About page never waits on
 * ifdian. Deeper pages are fetched lazily by {@link SponsorService#page(int)} instead.
 */
@Component
public class SponsorRefreshScheduler {

    private final SponsorService sponsors;

    /**
     * @param sponsors sponsor cache service
     */
    public SponsorRefreshScheduler(SponsorService sponsors) {
        this.sponsors = sponsors;
    }

    /**
     * Refreshes page one on every tick (six hours by default). No-op when ifdian is not configured.
     */
    @Scheduled(fixedDelayString = "${app.ifdian.refresh-delay-ms:21600000}")
    public void refresh() {
        sponsors.refreshFirstPage();
    }
}
