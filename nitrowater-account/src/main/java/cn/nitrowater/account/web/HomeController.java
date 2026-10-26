package cn.nitrowater.account.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Account root entry point.
 *
 * <p>Admins keep the console shortcut; every other authenticated identity is sent to the portal.
 * The old unconditional {@code redirect:/admin/} made any normal login without a saved request
 * end up in the admin console.</p>
 */
@Controller
public class HomeController {

    private final String defaultRedirect;

    /**
     * Creates the controller.
     *
     * @param defaultRedirect portal URL used for non-admin identities
     */
    public HomeController(
            @Value("${app.sso.default-redirect:http://localhost:5173}") String defaultRedirect) {
        this.defaultRedirect = defaultRedirect;
    }

    @GetMapping("/")
    public String index(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return "redirect:" + (admin ? "/admin/" : defaultRedirect);
    }
}
