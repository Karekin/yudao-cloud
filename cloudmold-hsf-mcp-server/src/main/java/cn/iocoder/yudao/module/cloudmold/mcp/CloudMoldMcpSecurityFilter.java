package cn.iocoder.yudao.module.cloudmold.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

final class CloudMoldMcpSecurityFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedAuthorization;
    private final Set<String> allowedOrigins;

    CloudMoldMcpSecurityFilter(CloudMoldMcpSecurityProperties properties) {
        properties.validate();
        this.expectedAuthorization = (BEARER_PREFIX + properties.getBearerToken())
                .getBytes(StandardCharsets.UTF_8);
        this.allowedOrigins = properties.allowedOriginSet();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed");
            return;
        }
        String authorization = request.getHeader("Authorization");
        byte[] presented = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedAuthorization, presented)) {
            response.setHeader("WWW-Authenticate", "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token is required");
            return;
        }
        chain.doFilter(request, response);
    }
}
