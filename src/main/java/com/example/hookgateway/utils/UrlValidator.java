package com.example.hookgateway.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SSRF 校验工具类
 * 防止 Webhook 转发请求到内网受保护的 IP 地址
 * V2: 支持 DNS 固定（解析后固定 IP），防止 DNS 重绑定攻击
 */
@Component
@Slf4j
public class UrlValidator {

    private final List<String> blockedIps;

    public UrlValidator(
            @Value("${app.security.ssrf.blocked-ips:127.0.0.1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.169.254}") String blockedIpsConfig) {
        this.blockedIps = Arrays.stream(blockedIpsConfig.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    /**
     * 验证并返回安全的请求目标（含已解析的 IP）
     * 
     * @throws IllegalArgumentException 如果 URL 不安全
     */
    public ValidatedTarget validate(String url) {
        try {
            URI uri = URI.create(url).normalize();
            String host = uri.getHost();

            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Host cannot be empty");
            }

            // V13: 协议白名单
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Blocked protocol: " + scheme);
            }

            // V13: 显式拦截通配/零地址
            if (host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
                throw new IllegalArgumentException("Blocked wildcard address: " + host);
            }

            // 1. 检查 host 是否在逻辑黑名单
            if (blockedIps.contains(host.toLowerCase())) {
                throw new IllegalArgumentException("Blocked host: " + host);
            }

            // 2. 解析 IP 地址并检查（DNS 固定核心）
            // 获取所有 IP，只要有一个是黑名单 IP，就应当警惕。
            // 严格模式：我们只使用第一个安全的 IP 进行连接。
            InetAddress[] addresses = InetAddress.getAllByName(host);
            InetAddress safeAddress = null;

            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    log.warn("[SSRF] Found blocked IP: {} for host: {}", addr.getHostAddress(), host);
                    // 只要有一个 IP 命中黑名单，就拒绝整个域名（避免 DNS 轮询绕过）
                    throw new IllegalArgumentException("Blocked IP detected: " + addr.getHostAddress());
                }
                if (safeAddress == null) {
                    safeAddress = addr;
                }
            }

            if (safeAddress == null) {
                throw new IllegalArgumentException("Could not resolve safe IP for host: " + host);
            }

            // 构造安全目标
            // HTTPS 需使用原域名以通过 SSL 校验（依赖 JVM DNS 缓存 TTL=60s 防护重绑定）
            // HTTP 可使用 IP 直连并设置 Host 请求头，进一步避免重绑定
            // 这里我们为调用方提供两种模式的信息。

            boolean useIpConnection = scheme.equalsIgnoreCase("http");
            String finalUrl = url;

            if (useIpConnection) {
                // 用 IP 重新构建 URL
                // 例如 http://1.2.3.4:8080/path?query
                int port = uri.getPort();
                String ip = safeAddress.getHostAddress();
                // 处理 IPv6 字面量
                if (ip.contains(":")) {
                    ip = "[" + ip + "]";
                }

                StringBuilder sb = new StringBuilder();
                sb.append(scheme).append("://").append(ip);
                if (port != -1) {
                    sb.append(":").append(port);
                }
                sb.append(uri.getRawPath());
                if (uri.getRawQuery() != null) {
                    sb.append("?").append(uri.getRawQuery());
                }
                finalUrl = sb.toString();
            }

            return new ValidatedTarget(finalUrl, host, useIpConnection);

        } catch (IllegalArgumentException e) {
            log.warn("[SSRF] Validation failed for {}: {}", url, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[SSRF] Unexpected error for {}: {}", url, e.getMessage());
            throw new IllegalArgumentException("Validation error");
        }
    }

    /**
     * 快速判断 URL 是否安全。
     *
     * @param url 目标 URL
     * @return true 表示安全
     */
    public boolean isSafeUrl(String url) {
        try {
            validate(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断 IP 是否属于受限范围。
     *
     * @param addr IP 地址
     * @return true 表示受限，false 表示允许
     */
    private boolean isBlockedAddress(InetAddress addr) {
        // 检查是否为回环或私有地址
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();

        // V14: IPv6 ULA（唯一本地地址）检查：fc00::/7
        if (bytes.length == 16) {
            if ((bytes[0] & 0xFE) == (byte) 0xFC) {
                return true;
            }
        }

        // 检查是否在显式指定的黑名单 CIDR/IP 中
        String ip = addr.getHostAddress();
        for (String blocked : blockedIps) {
            if (blocked.contains("/")) {
                if (isInSubnet(ip, blocked))
                    return true;
            } else if (ip.equals(blocked)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断 IP 是否落入指定 CIDR。
     *
     * @param ip   IP 地址
     * @param cidr CIDR 表示
     * @return true 表示命中
     */
    private boolean isInSubnet(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String subnet = parts[0];
            int bits = Integer.parseInt(parts[1]);

            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress subnetAddr = InetAddress.getByName(subnet);

            byte[] ipBytes = ipAddr.getAddress();
            byte[] subnetBytes = subnetAddr.getAddress();

            int fullBytes = bits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != subnetBytes[i])
                    return false;
            }

            int remainingBits = bits % 8;
            if (remainingBits > 0) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                return (ipBytes[fullBytes] & mask) == (subnetBytes[fullBytes] & mask);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验后的目标信息。
     */
    @Getter
    @RequiredArgsConstructor
    public static class ValidatedTarget {
        private final String targetUrl; // 可为 IP URL（HTTP）或原始 URL（HTTPS）
        private final String originalHost; // 用于 Host 请求头
        private final boolean useIpConnection; // 是否使用 IP 直连
    }
}
