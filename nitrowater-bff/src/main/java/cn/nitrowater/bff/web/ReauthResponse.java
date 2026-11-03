package cn.nitrowater.bff.web;

import cn.nitrowater.lib.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * The single 401 answer for "this session can no longer call upstream".
 *
 * <p>It mirrors {@code JsonAuthenticationEntryPoint} on purpose — same {@code session_expired}
 * envelope, same {@code X-Auth-Reason} header, same session-cookie expiry — so the SPA has one way
 * of detecting a lost session no matter whether the failure came from the security chain, from a
 * missing authorized client, or from a refresh the Authorization Server rejected.</p>
 *
 * <p>Both proxy controllers render it themselves instead of throwing: the AI route declares
 * {@code produces=text/event-stream}, and a JSON body written through the generic exception resolver
 * would trip "no converter for ApiResponse with preset Content-Type text/event-stream".</p>
 */
public final class ReauthResponse {

    /** User-facing message shared by every BFF re-authentication answer. */
    public static final String MESSAGE = "登录已失效，请重新登录";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ReauthResponse() {
    }

    /**
     * @param reason machine-readable cause (see {@code ReauthRequiredException#reason()})
     * @return JSON content type, the {@code X-Auth-Reason} header and the expired session cookie
     */
    public static HttpHeaders headers(String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth-Reason", reason);
        headers.add(HttpHeaders.SET_COOKIE, SessionCookies.expired().toString());
        return headers;
    }

    /**
     * @param reason machine-readable cause
     * @return the shared error envelope
     */
    public static ApiResponse<Map<String, Object>> envelope(String reason) {
        return ApiResponse.reject("session_expired", MESSAGE, Map.of("reason", reason));
    }

    /**
     * @param reason machine-readable cause
     * @return the envelope serialized for a streamed (byte[]) 401 body
     */
    public static byte[] body(String reason) {
        return MAPPER.writeValueAsBytes(envelope(reason));
    }
}
