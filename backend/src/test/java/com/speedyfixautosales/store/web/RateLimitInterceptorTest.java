package com.speedyfixautosales.store.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

    private final RateLimitInterceptor limits = new RateLimitInterceptor();

    private boolean post(String path, String client) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(client);
        return limits.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void eachClientGetsItsOwnHourlyAllowance() {
        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/part-requests", "203.0.113.7")).isTrue();
        }
        assertThatThrownBy(() -> post("/api/part-requests", "203.0.113.7")).isInstanceOf(TooManyRequestsException.class);
        // Someone else, and other actions, aren't affected.
        assertThat(post("/api/part-requests", "198.51.100.2")).isTrue();
        assertThat(post("/api/orders", "203.0.113.7")).isTrue();
    }

    @Test
    void readingAndPaymentCaptureAreNeverLimited() {
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/orders/SF-ABCDEFGH");
            get.setRemoteAddr("203.0.113.9");
            assertThat(limits.preHandle(get, new MockHttpServletResponse(), new Object())).isTrue();
            // Capturing an approved payment must always go through, or a paid order could be left unpaid.
            assertThat(post("/api/orders/SF-ABCDEFGH/paypal/capture", "203.0.113.9")).isTrue();
        }
    }

    @Test
    void anOverallCapStopsManyAddressesAtOnce() {
        for (int i = 0; i < 300; i++) {
            assertThat(post("/api/orders", "10.0." + (i / 250) + "." + (i % 250))).isTrue();
        }
        assertThatThrownBy(() -> post("/api/orders", "192.0.2.1")).isInstanceOf(TooManyRequestsException.class);
    }
}
