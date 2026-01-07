package com.example.hookgateway.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator(
            "127.0.0.1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16");

    @Test
    void testSafeUrls() {
        assertTrue(validator.isSafeUrl("https://1.1.1.1"));
        assertTrue(validator.isSafeUrl("http://8.8.8.8/webhook"));
    }

    @Test
    void testUnsafeProtocols() {
        assertFalse(validator.isSafeUrl("ftp://example.com/file"));
        assertFalse(validator.isSafeUrl("file:///etc/passwd"));
        assertFalse(validator.isSafeUrl("gopher://example.com"));
    }

    @Test
    void testBlockedIps() {
        assertFalse(validator.isSafeUrl("http://localhost:8080"));
        assertFalse(validator.isSafeUrl("http://127.0.0.1:8080"));
        assertFalse(validator.isSafeUrl("http://192.168.1.1"));
        assertFalse(validator.isSafeUrl("http://10.0.0.5"));
    }

    @Test
    void testIpv6Loopback() {
        assertFalse(validator.isSafeUrl("http://[::1]"));
    }
}
