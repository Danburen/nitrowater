package cn.nitrowater.resource.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalEndpointFilterTest {

    private final InternalEndpointFilter filter = new InternalEndpointFilter();

    @Test
    void acceptsLoopbackAndPrivateAddresses() {
        assertTrue(InternalEndpointFilter.isPrivateClient("127.0.0.1"));
        assertTrue(InternalEndpointFilter.isPrivateClient("::1"));
        assertTrue(InternalEndpointFilter.isPrivateClient("10.1.2.3"));
        assertTrue(InternalEndpointFilter.isPrivateClient("172.17.0.2"));
        assertTrue(InternalEndpointFilter.isPrivateClient("192.168.1.10"));
        assertTrue(InternalEndpointFilter.isPrivateClient("fd00::1"));
    }

    @Test
    void rejectsPublicAddresses() {
        assertFalse(InternalEndpointFilter.isPrivateClient("8.8.8.8"));
        assertFalse(InternalEndpointFilter.isPrivateClient("1.1.1.1"));
        assertFalse(InternalEndpointFilter.isPrivateClient("2001:4860:4860::8888"));
        assertFalse(InternalEndpointFilter.isPrivateClient(null));
        assertFalse(InternalEndpointFilter.isPrivateClient(" "));
    }

    @Test
    void rejectsInternalRequestFromPublicAddress() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/resource/publish");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest(), "request must not reach the controller");
    }

    @Test
    void letsInternalRequestFromLoopbackThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/resource/publish");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest());
    }
}
