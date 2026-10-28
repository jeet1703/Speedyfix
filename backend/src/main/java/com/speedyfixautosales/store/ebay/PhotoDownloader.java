package com.speedyfixautosales.store.ebay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Downloads a listing photo from eBay's image servers (i.ebayimg.com). Swapped for a stand-in in tests. */
@Component
public class PhotoDownloader {

    /** eBay's 1600 px photos are well under this; anything bigger isn't a listing photo. */
    private static final int MAX_BYTES = 15 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public byte[] download(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith("ebayimg.com")) {
            throw new IOException("Only eBay image addresses are downloaded: " + url);
        }
        try {
            HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new IOException("HTTP " + res.statusCode() + " for " + url);
            }
            if (res.body().length > MAX_BYTES) {
                throw new IOException("Photo too large: " + url);
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
