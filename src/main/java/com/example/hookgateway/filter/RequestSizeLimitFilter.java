package com.example.hookgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to limit the size of incoming requests to prevent DoS attacks.
 * Applied specifically to ingestion endpoints.
 * V2: Uses a wrapper to inspect the actual input stream size, strictly
 * enforcing the limit
 * regardless of Content-Length header presence (blocking Chunked Encoding
 * attacks).
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
            long contentLength = request.getContentLengthLong();

            // 1. Fast path: Check Header if present
            if (contentLength > MAX_REQUEST_SIZE) {
                reject((HttpServletResponse) response, path, contentLength);
                return;
            }

            // 2. Slow path: Wrap request to count bytes for chunked/streaming requests
            SizeLimitServletRequestWrapper wrapper = new SizeLimitServletRequestWrapper(httpRequest, MAX_REQUEST_SIZE);
            try {
                chain.doFilter(wrapper, response);
            } catch (SizeLimitExceededException e) {
                log.warn("[DoS Protection] Stream exceeded limit for {}: read {} bytes", path, e.getBytesRead());
                // If response is not committed, send error.
                // Mostly this exception happens during argument resolution in Controller.
                // We cannot easily sendError here if the stack has unwound, but usually we can
                // because
                // the exception propagates up. However, Spring might handle it.
                // To be safe, we rethrow if we can't handle it, or let Spring's
                // ExceptionHandler handle it.
                // But since we are in a Filter, we are outside DispatcherServlet's error
                // handling for *some* cases?
                // Actually, throwing a RuntimeException here might result in 500.

                // Let's try to handle it if response not committed.
                if (!response.isCommitted()) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                            "Payload too large");
                } else {
                    // Connection likely broken/reset by us closing stream?
                    // Just log.
                }
            }
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String path, long size) throws IOException {
        log.warn("[DoS Protection] Rejected request to {} with Content-Length: {}", path, size);
        response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload too large");
    }

    private static class SizeLimitExceededException extends RuntimeException {
        private final long bytesRead;

        public SizeLimitExceededException(long bytesRead) {
            super("Request size exceeded limit");
            this.bytesRead = bytesRead;
        }

        public long getBytesRead() {
            return bytesRead;
        }
    }

    private static class SizeLimitServletRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;
        private java.io.BufferedReader reader;

        public SizeLimitServletRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new SizeLimitInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                if (encoding == null) {
                    encoding = "UTF-8";
                }
                // Use our wrapped InputStream to ensure limit is enforced
                reader = new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), encoding));
            }
            return reader;
        }
    }

    private static class SizeLimitInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead = 0;

        public SizeLimitInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                checkLimit(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n != -1) {
                checkLimit(n);
            }
            return n;
        }

        private void checkLimit(int n) {
            bytesRead += n;
            if (bytesRead > maxBytes) {
                throw new SizeLimitExceededException(bytesRead);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
