package com.accsaber.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerExceptionResolver;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(400, "",
            "127.0.0.0/8,172.16.0.0/12,173.245.48.0/20,2606:4700::/32", mock(HandlerExceptionResolver.class));

    @Test
    void cloudflareHeaderIsHonouredFromACloudflareEdge() {
        assertThat(filter.resolveClientIp(request("173.245.48.10", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(filter.resolveClientIp(request("2606:4700:0:0:0:0:0:1", "203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void cloudflareHeaderIsHonouredFromTheDockerNetwork() {
        assertThat(filter.resolveClientIp(request("172.18.0.5", "203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void cloudflareHeaderIsIgnoredFromAnUntrustedClient() {
        assertThat(filter.resolveClientIp(request("198.51.100.20", "203.0.113.7"))).isEqualTo("198.51.100.20");
    }

    @Test
    void aHostnameRemoteAddressIsNeverTrusted() {
        assertThat(filter.resolveClientIp(request("evil.example", "203.0.113.7"))).isEqualTo("evil.example");
    }

    @Test
    void withoutTheHeaderTheRemoteAddressIsUsed() {
        assertThat(filter.resolveClientIp(request("173.245.48.10", null))).isEqualTo("173.245.48.10");
    }

    private static MockHttpServletRequest request(String remoteAddr, String cfConnectingIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/maps");
        request.setRemoteAddr(remoteAddr);
        if (cfConnectingIp != null) {
            request.addHeader("CF-Connecting-IP", cfConnectingIp);
        }
        return request;
    }
}
