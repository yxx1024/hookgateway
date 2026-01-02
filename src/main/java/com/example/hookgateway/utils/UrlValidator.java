package com.example.hookgateway.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SSRF 校验工具类
 * 防止 Webhook 转发请求到内网受保护的 IP 地址
 */
@Component
@Slf4j
public class UrlValidator {

    private final List<String> blockedIps;

    public UrlValidator(@Value("${app.security.ssrf.blocked-ips:127.0.0.1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.169.254}") String blockedIpsConfig) {
        this.blockedIps = Arrays.stream(blockedIpsConfig.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    /**
     * 验证 URL 是否安全（非内网 IP）
     */
    public boolean isSafeUrl(String url) {
        try {
            URI uri = URI.create(url).normalize();
            String host = uri.getHost();

            if (host == null || host.isEmpty()) {
                return false;
            }

            // 1. 检查 host 是否在逻辑黑名单（如 localhost）
            if (blockedIps.contains(host.toLowerCase())) {
                log.warn("[SSRF] Blocked host: {}", host);
                return false;
            }

            // 2. 解析 IP 地址并检查
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    log.warn("[SSRF] Blocked IP: {} for host: {}", addr.getHostAddress(), host);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.error("[SSRF] Validation error for URL: {}", url, e);
            return false;
        }
    }

    private boolean isBlockedAddress(InetAddress addr) {
        // 检查是否为回环或私有地址
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
            return true;
        }

        // 检查是否在显式指定的黑名单 CIDR/IP 中
        String ip = addr.getHostAddress();
        for (String blocked : blockedIps) {
            if (blocked.contains("/")) {
                if (isInSubnet(ip, blocked)) return true;
            } else if (ip.equals(blocked)) {
                return true;
            }
        }

        return false;
    }

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
                if (ipBytes[i] != subnetBytes[i]) return false;
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
}
