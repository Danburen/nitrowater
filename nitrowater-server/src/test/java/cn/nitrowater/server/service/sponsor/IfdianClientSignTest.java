package cn.nitrowater.server.service.sponsor;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the ifdian signature contract: the exact concatenation order and lowercase hex digest.
 */
class IfdianClientSignTest {

    @Test
    void signsTheDocumentedConcatenationWithoutSeparators() {
        String params = "{\"page\":1,\"per_page\":20}";

        String sign = IfdianClient.sign("test-token", params, 1700000000L, "12345");

        assertEquals("36eb8ef9e874baf1879a7bc038f08837", sign);
    }

    @Test
    void expandsJsonParamsAsAUriVariableInsteadOfATemplate() {
        String params = "{\"page\":1,\"per_page\":20}";

        URI uri = IfdianClient.queryUri("https://ifdian.net/api/open", "12345", params,
                1700000000L, "sig");

        Map<String, String> query = queryParams(uri);
        assertEquals(params, query.get("params"));
        assertEquals("12345", query.get("user_id"));
        assertEquals("1700000000", query.get("ts"));
        assertEquals("sig", query.get("sign"));
    }

    private static Map<String, String> queryParams(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            result.put(pair.substring(0, separator),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return result;
    }
}
