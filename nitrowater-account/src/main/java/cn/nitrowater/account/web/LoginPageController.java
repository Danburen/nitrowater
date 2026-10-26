package cn.nitrowater.account.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SSO hosted auth pages. Forwards the public auth routes to the Vue SPA under
 * {@code /auth/} so login and registration stay on the same origin as the
 * Authorization Server (cookies / saved-request / OIDC resume all stay same-origin).
 *
 * <p>The SPA uses history routes {@code /login} and {@code /register}; both Spring
 * mappings forward to the same shell, and the browser keeps the real URL so the
 * {@code error} / {@code redirect} query parameters survive. Serving via the static
 * resource handler (instead of reading the classpath here) lets
 * {@code spring.web.resources.static-locations} point at the source tree in dev, so a
 * rebuilt {@code static/auth/*} reflects on refresh without a restart.</p>
 *
 * <p>Login remains a native form POST to {@code /login} handled by Spring Security; the
 * {@code SsoAuthenticationProvider} reads the {@code captcha} / {@code deviceFp} fields.
 * Register is a client-side flow calling {@code /api/auth/**}.</p>
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String login() {
        return "forward:/auth/index.html";
    }

    @GetMapping("/register")
    public String register() {
        return "forward:/auth/index.html";
    }
}
