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

    /**
     * 执行请求大小限制过滤。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
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

    /**
     * 返回 413 并记录日志。
     *
     * @param response 响应
     * @param path     请求路径
     * @param size     请求体大小
     * @throws IOException IO 异常
     */
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

    /**
     * 请求包装器：限制输入流读取大小。
     */
    private static class SizeLimitServletRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;
        private java.io.BufferedReader reader;

        public SizeLimitServletRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        /**
         * 获取受限输入流。
         *
         * @return 输入流
         * @throws IOException IO 异常
         */
        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new SizeLimitInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        /**
         * 获取受限字符读取器。
         *
         * @return 读取器
         * @throws IOException IO 异常
         */
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

    /**
     * 输入流包装器：统计读取字节并限制大小。
     */
    private static class SizeLimitInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead = 0;

        public SizeLimitInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        /**
         * 读取一个字节并检查限制。
         *
         * @return 读取的字节
         * @throws IOException IO 异常
         */
        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                checkLimit(1);
            }
            return b;
        }

        /**
         * 读取字节数组并检查限制。
         *
         * @param b   缓冲区
         * @param off 偏移
         * @param len 长度
         * @return 读取字节数
         * @throws IOException IO 异常
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n != -1) {
                checkLimit(n);
            }
            return n;
        }

        /**
         * 累加并检查读取大小。
         *
         * @param n 本次读取字节数
         */
        private void checkLimit(int n) {
            bytesRead += n;
            if (bytesRead > maxBytes) {
                throw new SizeLimitExceededException(bytesRead);
            }
        }

        /**
         * 是否读取完成。
         *
         * @return true 表示完成
         */
        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        /**
         * 是否可读。
         *
         * @return true 表示可读
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 设置读取监听器。
         *
         * @param listener 监听器
         */
        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
