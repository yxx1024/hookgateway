package com.example.hookgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to limit the size of incoming requests to prevent DoS attacks.
 * Applied specifically to ingestion endpoints.
 */
@Component
@Slf4j
public class RequestSizeLimitFilter implements Filter {

    private static final long MAX_REQUEST_SIZE = 2 * 1024 * 1024; // 2MB

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Only apply to /hooks/** endpoints
        if (path.startsWith("/hooks/")) {
            long length = request.getContentLengthLong();

            // Check Content-Length header if present
            if (length > MAX_REQUEST_SIZE) {
                log.warn("[DoS Protection] Rejected request to {} with Content-Length: {}", path, length);
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Payload too large");
                return;
            }

            // Note: For chunked transfer encoding (length == -1), we would need a
            // sophisticated wrapper
            // to count bytes as they are read. For this fix, we primarily rely on
            // Content-Length
            // and the container's max-post-size for absolute safety, but this filter
            // provides
            // an early rejection layer.
            // Spring Boot's application.properties server.tomcat.max-swallow-size also
            // helps.
        }

        chain.doFilter(request, response);
    }
}
