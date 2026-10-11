package cn.nitrowater.server.config;

import cn.nitrowater.server.config.properties.SandboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the {@link RestClient} used to call the Piston execution service.
 *
 * <p>Timeouts are set explicitly: the default RestClient has none, so a stuck Piston call would
 * otherwise pin a request thread indefinitely. Read timeout is intentionally larger than the
 * sandbox compile + run budget.</p>
 *
 * <p>The client is pinned to <b>HTTP/1.1</b>: the JDK {@link HttpClient} defaults to HTTP/2 and, for
 * cleartext, issues an {@code Upgrade: h2c} handshake. Piston (Node/Express) does not support h2c, so
 * that upgrade corrupts request framing and every call fails with {@code 400 Bad Request}.</p>
 */
@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class PistonConfig {

    /** Piston API client; base URL and timeouts come from {@code app.sandbox.*}. */
    @Bean
    public RestClient pistonRestClient(SandboxProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
