package cn.nitrowater.server.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.service.sponsor.SponsorService;
import cn.nitrowater.server.service.sponsor.SponsorView;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous sponsor list for the About page. BFF permits {@code /api/public/**} without a session
 * and proxies it here; the server's security chain also leaves this prefix open.
 */
@RestController
public class PublicSponsorController {

    private final SponsorService sponsors;

    /**
     * @param sponsors sponsor cache service
     */
    public PublicSponsorController(SponsorService sponsors) {
        this.sponsors = sponsors;
    }

    /**
     * @param page zero-based page number, 20 sponsors per page by default
     * @return one page of sponsors inside the standard {@code ApiResponse} envelope
     */
    @GetMapping("/api/public/sponsors")
    public ApiResponse<Page<SponsorView>> list(@RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(sponsors.page(page));
    }
}
