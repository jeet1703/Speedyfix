package com.speedyfixautosales.store.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * eBay's Browse API with an application token (client credentials). Public listing data only, so no seller sign-in
 * is needed. Fetches 20 listings per call with getItems, falling back to getItem one at a time if a batch fails.
 * eBay's default allowance is 5,000 calls a day per API.
 */
@Component
public class EbayBrowseClient implements EbayItemSource {

    private static final Logger log = LoggerFactory.getLogger(EbayBrowseClient.class);
    private static final String API = "https://api.ebay.com";
    private static final Duration PAUSE = Duration.ofMillis(300);

    private final String clientId;
    private final String clientSecret;
    private final String marketplace;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public EbayBrowseClient(@Value("${ebay.client-id:}") String clientId, @Value("${ebay.client-secret:}") String clientSecret,
                            @Value("${ebay.marketplace:EBAY_GB}") String marketplace) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.marketplace = marketplace;
    }

    @Override
    public boolean enabled() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    @Override
    public Map<String, ItemDetails> fetch(List<String> itemNumbers) {
        Map<String, ItemDetails> found = new LinkedHashMap<>();
        if (itemNumbers.isEmpty()) {
            return found;
        }
        String ids = itemNumbers.stream().map(n -> "v1|" + n + "|0").collect(Collectors.joining(","));
        try {
            JsonNode body = get("/buy/browse/v1/item/?item_ids=" + URLEncoder.encode(ids, StandardCharsets.UTF_8));
            for (JsonNode item : body.path("items")) {
                ItemDetails d = details(item);
                found.put(d.itemNumber(), d);
            }
        } catch (IOException e) {
            log.warn("Batch of {} failed ({}); fetching one at a time", itemNumbers.size(), e.getMessage());
            for (String n : itemNumbers) {
                try {
                    ItemDetails d = details(get("/buy/browse/v1/item/" + URLEncoder.encode("v1|" + n + "|0", StandardCharsets.UTF_8)));
                    found.put(d.itemNumber(), d);
                } catch (IOException single) {
                    log.info("eBay item {} not returned: {}", n, single.getMessage());
                }
            }
        }
        return found;
    }

    private ItemDetails details(JsonNode item) {
        List<String> images = new ArrayList<>();
        if (item.path("image").hasNonNull("imageUrl")) {
            images.add(largest(item.path("image").path("imageUrl").asText()));
        }
        for (JsonNode extra : item.path("additionalImages")) {
            if (extra.hasNonNull("imageUrl")) {
                images.add(largest(extra.path("imageUrl").asText()));
            }
        }
        List<Aspect> aspects = new ArrayList<>();
        for (JsonNode a : item.path("localizedAspects")) {
            aspects.add(new Aspect(a.path("name").asText(), a.path("value").asText()));
        }
        String legacyId = item.hasNonNull("legacyItemId") ? item.path("legacyItemId").asText()
                : item.path("itemId").asText().replaceAll("^v1\\|(\\d+)\\|.*$", "$1");
        return new ItemDetails(legacyId, images.stream().distinct().toList(), aspects, text(item, "brand"), text(item, "mpn"),
                text(item, "shortDescription"));
    }

    /** eBay photo addresses end in s-l<size>.jpg; ask for the 1600 px version. */
    static String largest(String url) {
        return url.replaceAll("/s-l\\d+\\.(jpg|jpeg|png|webp)", "/s-l1600.jpg");
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank() ? node.path(field).asText().trim() : null;
    }

    private JsonNode get(String path) throws IOException {
        for (int attempt = 1; ; attempt++) {
            pause(PAUSE);
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token())
                    .header("X-EBAY-C-MARKETPLACE-ID", marketplace)
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> res = send(request);
            if (res.statusCode() == 429 && attempt <= 3) {
                log.warn("eBay rate limit reached; waiting a minute");
                pause(Duration.ofMinutes(1));
                continue;
            }
            if (res.statusCode() == 401 && attempt == 1) {
                tokenExpires = Instant.EPOCH;
                continue;
            }
            if (res.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + res.statusCode() + ": " + res.body().substring(0, Math.min(200, res.body().length())));
            }
            return json.readTree(res.body());
        }
    }

    /** The application token, fetched again shortly before it expires (they last about two hours). */
    private synchronized String token() throws IOException {
        if (token != null && Instant.now().isBefore(tokenExpires)) {
            return token;
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/identity/v1/oauth2/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope="
                        + URLEncoder.encode("https://api.ebay.com/oauth/api_scope", StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> res = send(request);
        if (res.statusCode() != 200) {
            throw new IOException("eBay refused the application keys (HTTP " + res.statusCode() + "). Check EBAY_CLIENT_ID and EBAY_CLIENT_SECRET.");
        }
        JsonNode body = json.readTree(res.body());
        token = body.path("access_token").asText();
        tokenExpires = Instant.now().plusSeconds(Math.max(60, body.path("expires_in").asLong(7200) - 120));
        return token;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static void pause(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
