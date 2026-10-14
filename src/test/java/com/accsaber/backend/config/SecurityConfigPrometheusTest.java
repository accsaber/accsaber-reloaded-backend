package com.accsaber.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigPrometheusTest {

    @Test
    void theInternalScraperOnTheDockerNetworkIsAllowed() {
        assertThat(SecurityConfig.isInternalScrape(request("172.18.0.7", null))).isTrue();
        assertThat(SecurityConfig.isInternalScrape(request("127.0.0.1", null))).isTrue();
    }

    @Test
    void aPublicAddressIsRefused() {
        assertThat(SecurityConfig.isInternalScrape(request("198.51.100.20", null))).isFalse();
    }

    @Test
    void anythingThatCameThroughCloudflareIsRefused() {
        assertThat(SecurityConfig.isInternalScrape(request("172.18.0.7", "198.51.100.20"))).isFalse();
    }

    @Test
    void otherPathsAreNotMatched() {
        MockHttpServletRequest request = request("172.18.0.7", null);
        request.setRequestURI("/actuator/health");

        assertThat(SecurityConfig.isInternalScrape(request)).isFalse();
    }

    private static MockHttpServletRequest request(String remoteAddr, String cfConnectingIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr(remoteAddr);
        if (cfConnectingIp != null) {
            request.addHeader("CF-Connecting-IP", cfConnectingIp);
        }
        return request;
    }
}
