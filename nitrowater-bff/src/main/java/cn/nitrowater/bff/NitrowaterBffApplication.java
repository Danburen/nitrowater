package cn.nitrowater.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend-for-Frontend (Token Handler) for the NitroWater SSO.
 *
 * <p>The browser holds only an HttpOnly session cookie; this service is an OIDC
 * <em>confidential</em> client that keeps the access/refresh tokens server-side and
 * proxies business calls to the resource server with a Bearer access token. Because the
 * client is confidential, the Authorization Server issues a refresh token and silent
 * renewal happens here (no iframe, no token in the browser).</p>
 */
@SpringBootApplication
public class NitrowaterBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(NitrowaterBffApplication.class, args);
    }
}
