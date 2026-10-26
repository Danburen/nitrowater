package cn.nitrowater.resource.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Restricts {@code /internal/**} to loopback and private networks.
 *
 * <p>The endpoints already demand a shared-secret token; this is the network half of the same rule —
 * ops tooling on the machine or the intranet, never the public internet. It runs before the security
 * chain, so a rejected request never reaches a controller (and never has its body read).</p>
 *
 * <p>A reverse proxy in front of the service appears as its own private address, so the token
 * remains the primary control; this filter only removes publicly-sourced traffic.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalEndpointFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalEndpointFilter.class);

    private static final String INTERNAL_PATH = "/internal";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean internal = uri.equals(INTERNAL_PATH) || uri.startsWith(INTERNAL_PATH + "/");
        if (!internal || isPrivateClient(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected internal request from {} {} ({})", request.getRemoteAddr(), uri,
                request.getHeader("X-Forwarded-For"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"internal_forbidden\",\"message\":\"Internal endpoints are only reachable from a private network\"}");
    }

    /**
     * @param remoteAddr the connection's peer address (never a header)
     * @return whether the address is loopback, RFC 1918, link-local or an IPv6 unique-local address
     */
    static boolean isPrivateClient(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            byte[] raw = address.getAddress();
            // IPv6 unique-local fc00::/7 is not covered by isSiteLocalAddress().
            return raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
