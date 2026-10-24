package com.speedyfixautosales.store.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Render's free plan puts the service to sleep after 15 minutes without traffic.
 * Pinging our own public URL every 14 minutes keeps it awake. Render sets
 * RENDER_EXTERNAL_URL automatically, so this does nothing when running locally.
 */
@Component
public class KeepAlive {

    private static final Logger log = LoggerFactory.getLogger(KeepAlive.class);

    private final String url;
    private final RestClient client;

    public KeepAlive(@Value("${keep-alive.url:${RENDER_EXTERNAL_URL:}}") String baseUrl) {
        this.url = baseUrl.isBlank() ? "" : baseUrl.replaceAll("/$", "") + "/api/site";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT14M")
    void ping() {
        if (url.isEmpty()) {
            return;
        }
        try {
            client.get().uri(url).retrieve().toBodilessEntity();
            log.debug("Keep-alive ping to {} succeeded", url);
        } catch (RuntimeException e) {
            log.warn("Keep-alive ping to {} failed: {}", url, e.getMessage());
        }
    }
}
