package com.speedyfixautosales.store.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final String VEHICLE_PARTS = "131090";
    private static final int SEARCH_PAGE = 200;

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

    @Override
    public Optional<String> sellerOf(String itemNumber) throws IOException {
        JsonNode item = get("/buy/browse/v1/item/get_item_by_legacy_id?legacy_item_id=" + URLEncoder.encode(itemNumber, StandardCharsets.UTF_8));
        return Optional.ofNullable(text(item.path("seller"), "username"));
    }

    /** eBay's search returns at most 10,000 results, 200 a page; everything the shop sells is under Vehicle Parts & Accessories. */
    @Override
    public List<Listing> sellerListings(String seller) throws IOException {
        String filter = URLEncoder.encode("sellers:{" + seller + "},buyingOptions:{FIXED_PRICE}", StandardCharsets.UTF_8);
        // Newest first: eBay's default "best match" order reshuffles between pages, so some listings come twice and
        // others never, and those would look ended.
        Map<String, Listing> listings = new LinkedHashMap<>();
        int total = Integer.MAX_VALUE;
        for (int offset = 0; offset < Math.min(total, 10_000); offset += SEARCH_PAGE) {
            JsonNode page = get("/buy/browse/v1/item_summary/search?category_ids=" + VEHICLE_PARTS + "&filter=" + filter
                    + "&sort=newlyListed&limit=" + SEARCH_PAGE + "&offset=" + offset);
            total = page.path("total").asInt(0);
            for (JsonNode s : page.path("itemSummaries")) {
                String number = s.hasNonNull("legacyItemId") ? s.path("legacyItemId").asText()
                        : s.path("itemId").asText().replaceAll("^v1\\|(\\d+)\\|.*$", "$1");
                JsonNode category = s.path("categories").path(0);
                String created = text(s, "itemCreationDate");
                // During an eBay sale, price is the discounted one; the shop keeps the listing's own price.
                JsonNode price = s.path("marketingPrice").path("originalPrice").hasNonNull("value")
                        ? s.path("marketingPrice").path("originalPrice") : s.path("price");
                listings.put(number, new Listing(number, text(s, "title"),
                        price.hasNonNull("value") ? new BigDecimal(price.path("value").asText()) : null,
                        text(s, "condition"), text(category, "categoryId"), text(category, "categoryName"),
                        created == null ? null : LocalDate.parse(created.substring(0, 10))));
            }
            if (page.path("itemSummaries").isEmpty()) {
                break;
            }
        }
        if (listings.size() < Math.min(total, 10_000)) {
            throw new IOException("eBay listed " + listings.size() + " of " + total + " listings");
        }
        return List.copyOf(listings.values());
    }

    @Override
    public boolean listed(String itemNumber) throws IOException {
        try {
            get("/buy/browse/v1/item/get_item_by_legacy_id?legacy_item_id=" + URLEncoder.encode(itemNumber, StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP 404")) {
                return false;
            }
            throw e;
        }
    }

    /** eBay UK's category tree is 3; 33615 (Engines) carries the full car compatibility catalogue. */
    @Override
    public List<String> compatibilityValues(String property, String filter) throws IOException {
        String path = "/commerce/taxonomy/v1/category_tree/3/get_compatibility_property_values?category_id=33615&compatibility_property="
                + URLEncoder.encode(property, StandardCharsets.UTF_8)
                + (filter == null || filter.isBlank() ? "" : "&filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8));
        List<String> values = new ArrayList<>();
        for (JsonNode v : get(path).path("compatibilityPropertyValues")) {
            if (v.hasNonNull("value") && !v.path("value").asText().isBlank()) {
                values.add(v.path("value").asText());
            }
        }
        return values;
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
                text(item, "shortDescription"), text(item, "description"));
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
