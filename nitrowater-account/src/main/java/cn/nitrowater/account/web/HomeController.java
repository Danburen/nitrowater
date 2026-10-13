package cn.nitrowater.account.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Account root entry point.
 *
 * <p>The account service owns no page at the bare {@code /}: it hosts the SSO auth pages
 * ({@code /login}, {@code /register}) and the admin console SPA ({@code /admin/}). A browser can
 * still land on {@code /} two ways — Spring Security's post-logout default and the
 * {@code HttpSessionRequestCache} saved-request redirect (which carries a benign {@code ?continue}
 * query) — and without a mapping that path resolves to no resource and returns a 404. Forwarding
 * the root to the console lets the console's own guard take over (bouncing anonymous users to
 * {@code /login} with a saved request for {@code /admin/}).</p>
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "redirect:/admin/";
    }
}
