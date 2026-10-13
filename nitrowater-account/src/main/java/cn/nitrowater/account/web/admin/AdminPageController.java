package cn.nitrowater.account.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the admin console SPA shell on the account origin.
 *
 * <p>The console is a Vue SPA built into {@code /static/admin} with hash routing
 * ({@code /admin/#/users}), so every entry passes through {@code /admin/} and only
 * needs the shell HTML; the router resolves the rest client-side. These routes
 * forward the bare paths to the built {@code index.html}, which the static-resource
 * handler reads from {@code spring.web.resources.static-locations} (source tree in
 * dev, classpath in the jar).</p>
 */
@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String index() {
        return "forward:/admin/index.html";
    }
}
