package cn.nitrowater.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyHeadersTest {

    @Test
    void forwardsOnlyAllowlistedRequestHeaders() {
        assertTrue(ProxyHeaders.isForwarded("Accept"));
        assertTrue(ProxyHeaders.isForwarded("content-type"));
        assertTrue(ProxyHeaders.isForwarded("Range"));

        // Identity / routing headers are exactly what must not cross the hop.
        assertFalse(ProxyHeaders.isForwarded("X-User-Uid"));
        assertFalse(ProxyHeaders.isForwarded("X-Forwarded-Proto"));
        assertFalse(ProxyHeaders.isForwarded("X-Original-URL"));
        assertFalse(ProxyHeaders.isForwarded("Cookie"));
        assertFalse(ProxyHeaders.isForwarded("Authorization"));
    }

    @Test
    void neverRelaysSetCookieOrHopByHopHeaders() {
        assertFalse(ProxyHeaders.isRelayableResponseHeader("Set-Cookie"));
        assertFalse(ProxyHeaders.isRelayableResponseHeader("Transfer-Encoding"));
        assertFalse(ProxyHeaders.isRelayableResponseHeader("Content-Length"));

        assertTrue(ProxyHeaders.isRelayableResponseHeader("Content-Type"));
        assertTrue(ProxyHeaders.isRelayableResponseHeader("ETag"));
    }

    @Test
    void copiesOnlyAllowedHeadersFromTheRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");
        request.addHeader("X-User-Uid", "1");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("Cookie", "JSESSIONID=abc");

        Map<String, String> forwarded = new LinkedHashMap<>();
        ProxyHeaders.forEachForwardedHeader(request, forwarded::put);

        assertEquals(Map.of("Accept", "application/json"), forwarded);
    }
}
