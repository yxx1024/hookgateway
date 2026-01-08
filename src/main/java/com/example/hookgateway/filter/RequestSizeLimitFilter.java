package com.example.hookgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 限制请求体大小，防止 DoS。
 * 仅对摄入端点生效。
 * V2: 使用包装器统计真实读取字节，避免因缺失 Content-Length 被绕过
 * （阻止 Chunked 编码攻击）。
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

        // 仅对 /hooks/** 生效
        if (path.startsWith("/hooks/")) {
            long contentLength = request.getContentLengthLong();

            // 1. 快路径：如果有 Content-Length 先做拦截
            if (contentLength > MAX_REQUEST_SIZE) {
                reject((HttpServletResponse) response, path, contentLength);
                return;
            }

            // 2. 慢路径：包装流，统计实际读取字节（覆盖分块/流式请求）
            SizeLimitServletRequestWrapper wrapper = new SizeLimitServletRequestWrapper(httpRequest, MAX_REQUEST_SIZE);
            try {
                chain.doFilter(wrapper, response);
            } catch (SizeLimitExceededException e) {
                log.warn("[DoS Protection] Stream exceeded limit for {}: read {} bytes", path, e.getBytesRead());
                // 若响应未提交，返回 413；否则仅记录日志，避免二次写入异常。
                if (!response.isCommitted()) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                            "Payload too large");
                } else {
                    // 连接可能已被关闭或重置，仅记录日志即可
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
                // 使用包装后的 InputStream，确保大小限制生效
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
