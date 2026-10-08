package cn.nitrowater.account.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SSO hosted auth pages (Phase 2). Forwards the branded page routes to the static
 * resources under {@code /auth/} so the pages are served on the same origin as the
 * Authorization Server (cookies / saved-request / OIDC resume all stay same-origin).
 *
 * <p>Serving via the static-resource handler (instead of reading the classpath here)
 * lets {@code spring.web.resources.static-locations} point at the source tree in dev,
 * so edits to {@code static/auth/*} reflect on refresh without a restart.</p>
 *
 * <p>Login is a native form POST to {@code /login} handled by Spring Security; the
 * {@code SsoAuthenticationProvider} reads the {@code captcha} / {@code deviceFp} fields.
 * Register is a client-side flow calling {@code /api/auth/**}.</p>
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String login() {
        return "forward:/auth/login.html";
    }

    @GetMapping("/register")
    public String register() {
        return "forward:/auth/register.html";
    }
}
