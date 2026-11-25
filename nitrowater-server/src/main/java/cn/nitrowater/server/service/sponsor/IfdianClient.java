package cn.nitrowater.server.service.sponsor;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.config.IfdianProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signed GET client for the ifdian OpenAPI.
 *
 * <p>Every call sends {@code user_id}, a JSON-string {@code params}, a seconds {@code ts} and an
 * MD5 {@code sign}. The signature source is
 * {@code token + "params" + params + "ts" + ts + "user_id" + user_id} with no separators; the token
 * itself is never part of the query string.</p>
 */
@Component
public class IfdianClient {

    private static final Logger log = LoggerFactory.getLogger(IfdianClient.class);

    private final IfdianProperties properties;
    private final RestClient rest;

    /**
     * @param properties ifdian credentials and timeouts
     */
    public IfdianClient(IfdianProperties properties) {
        this.properties = properties;
        int connectMs = properties.connectTimeoutMs() > 0 ? properties.connectTimeoutMs() : 3000;
        int readMs = properties.readTimeoutMs() > 0 ? properties.readTimeoutMs() : 10000;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readMs));
        this.rest = RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        // Never log the token; only whether both credentials were supplied.
        log.info("ifdian sponsors: {} (user_id present={}, token present={})",
                properties.configured() ? "enabled" : "disabled",
                properties.userId() != null && !properties.userId().isBlank(),
                properties.token() != null && !properties.token().isBlank());
    }

    /**
     * Fetches one page of sponsors from ifdian.
     *
     * @param ifdianPage one-based page number (the public API is zero-based; callers convert)
     * @param perPage number of records on the page
     * @return the raw {@code ec/em/data} envelope, already checked for {@code ec == 200}
     */
    public JsonNode querySponsors(int ifdianPage, int perPage) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", ifdianPage);
        params.put("per_page", perPage);
        String paramsJson = JsonUtil.toJson(params);
        long ts = Instant.now().getEpochSecond();

        URI uri = queryUri(properties.baseUrl(), properties.userId(), paramsJson, ts,
                sign(properties.token(), paramsJson, ts, properties.userId()));
        JsonNode body = rest.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
        int ec = body == null ? -1 : body.path("ec").asInt(-1);
        if (ec != 200) {
            String em = body == null ? "empty response" : body.path("em").asText("unknown error");
            throw new IllegalStateException("ifdian query-sponsor failed: " + ec + " " + em);
        }
        return body;
    }

    /**
     * Builds the signed request URI. The JSON {@code params} must be supplied as a URI variable:
     * its braces would otherwise be parsed as template placeholders during expansion.
     *
     * @param baseUrl ifdian OpenAPI base URL
     * @param userId ifdian user id
     * @param paramsJson exact JSON string sent as the {@code params} query value
     * @param ts seconds timestamp sent as {@code ts}
     * @param signature MD5 signature sent as {@code sign}
     * @return the fully expanded, encoded request URI
     */
    static URI queryUri(String baseUrl, String userId, String paramsJson, long ts, String signature) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/query-sponsor")
                .queryParam("user_id", userId)
                .queryParam("params", "{params}")
                .queryParam("ts", ts)
                .queryParam("sign", signature)
                .build(paramsJson);
    }

    /**
     * Derives the request signature.
     *
     * @param token shared secret, never transmitted
     * @param params exact JSON string sent as the {@code params} query value
     * @param ts seconds timestamp sent as {@code ts}
     * @param userId ifdian user id sent as {@code user_id}
     * @return lowercase hex MD5 digest
     */
    static String sign(String token, String params, long ts, String userId) {
        String source = token + "params" + params + "ts" + ts + "user_id" + userId;
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is not available in this JVM", ex);
        }
    }
}
