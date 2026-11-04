package com.speedyfixautosales.store.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PayPal's Orders API (v2) with the shop's REST app keys: PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET, from
 * developer.paypal.com, Apps & Credentials. PAYPAL_ENVIRONMENT is "sandbox" (test money, the default) or "live".
 */
@Component
public class PayPalClient {

    private final String clientId;
    private final String clientSecret;
    private final String api;
    private final boolean live;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    /** What PayPal says about a payment after capture. */
    public record Capture(String status, String captureId, BigDecimal amount, String currency, String reference) {
    }

    public PayPalClient(@Value("${paypal.client-id:}") String clientId, @Value("${paypal.client-secret:}") String clientSecret,
                        @Value("${paypal.environment:sandbox}") String environment) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.live = "live".equalsIgnoreCase(environment.trim());
        this.api = live ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    public boolean enabled() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    /** Public: the browser needs it to load PayPal's buttons. Empty when PayPal isn't set up. */
    public String publicClientId() {
        return enabled() ? clientId : "";
    }

    public boolean live() {
        return live;
    }

    /** Starts a PayPal payment for the order's total. Returns PayPal's order id for the buttons. */
    public String createOrder(Order order, String brandName) throws IOException {
        return createOrder(order, brandName, false);
    }

    /**
     * @param card true for the card form on the shop's own checkout (PayPal's card fields): the card is charged through
     *             the shop's PayPal account without the shopper going through PayPal's own checkout (which refuses some
     *             countries' cards, like India's), with the bank's security check (3D Secure) where the card needs it.
     */
    public String createOrder(Order order, String brandName, boolean card) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.put("intent", "CAPTURE");
        ObjectNode unit = body.putArray("purchase_units").addObject();
        unit.put("reference_id", order.reference());
        unit.put("custom_id", order.reference());
        unit.put("invoice_id", order.reference());
        unit.put("description", brandName + " order " + order.reference());
        unit.putObject("amount").put("currency_code", "GBP").put("value", order.total().setScale(2).toPlainString());
        if (card) {
            body.putObject("payment_source").putObject("card").putObject("attributes").putObject("verification")
                    .put("method", "SCA_WHEN_REQUIRED");
        } else {
            // We already have the delivery address from checkout, so PayPal doesn't ask for one.
            body.putObject("payment_source").putObject("paypal").putObject("experience_context")
                    .put("brand_name", brandName)
                    .put("shipping_preference", "NO_SHIPPING")
                    .put("user_action", "PAY_NOW");
        }
        // No request id: a shopper who comes back later gets a fresh PayPal order (an unpaid one expires after a few hours).
        JsonNode res = send("POST", "/v2/checkout/orders", body.toString(), null);
        String id = res.path("id").asText("");
        if (id.isEmpty()) {
            throw new IOException("PayPal didn’t return an order id.");
        }
        return id;
    }

    /** Takes the payment the shopper approved. If it was already taken, PayPal's record of it is returned instead. */
    public Capture capture(String paypalOrderId) throws IOException {
        JsonNode res;
        try {
            res = send("POST", "/v2/checkout/orders/" + paypalOrderId + "/capture", "{}", paypalOrderId + "-capture");
        } catch (PayPalException e) {
            if (!e.getMessage().contains("ORDER_ALREADY_CAPTURED")) {
                throw e;
            }
            res = send("GET", "/v2/checkout/orders/" + paypalOrderId, null, null);
        }
        JsonNode unit = res.path("purchase_units").path(0);
        JsonNode capture = unit.path("payments").path("captures").path(0);
        JsonNode amount = capture.path("amount");
        return new Capture(capture.path("status").asText(res.path("status").asText("")), capture.path("id").asText(null),
                amount.hasNonNull("value") ? new BigDecimal(amount.path("value").asText()) : null,
                amount.path("currency_code").asText(null),
                capture.hasNonNull("custom_id") ? capture.path("custom_id").asText() : unit.path("custom_id").asText(unit.path("reference_id").asText(null)));
    }

    /** PayPal refused the request; the message carries PayPal's error name (like INSTRUMENT_DECLINED). */
    public static class PayPalException extends IOException {
        private static final long serialVersionUID = 1L;

        PayPalException(String message) {
            super(message);
        }
    }

    private JsonNode send(String method, String path, String body, String requestId) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(api + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (requestId != null) {
            // Makes a retried request safe: PayPal treats the same id as the same request.
            request.header("PayPal-Request-Id", requestId);
        }
        HttpResponse<String> res = sendRaw(request.build());
        if (res.statusCode() / 100 != 2) {
            JsonNode err = parse(res.body());
            String issue = err.path("details").path(0).path("issue").asText(err.path("name").asText("HTTP " + res.statusCode()));
            throw new PayPalException(issue + ": " + err.path("message").asText(res.body().substring(0, Math.min(200, res.body().length()))));
        }
        return parse(res.body());
    }

    private synchronized String token() throws IOException {
        if (!enabled()) {
            throw new IOException("PayPal isn’t set up (PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET).");
        }
        if (token != null && Instant.now().isBefore(tokenExpires)) {
            return token;
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> res = sendRaw(HttpRequest.newBuilder(URI.create(api + "/v1/oauth2/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build());
        if (res.statusCode() != 200) {
            throw new IOException("PayPal refused the app keys (HTTP " + res.statusCode() + "). Check PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET and PAYPAL_ENVIRONMENT.");
        }
        JsonNode body = parse(res.body());
        token = body.path("access_token").asText();
        tokenExpires = Instant.now().plusSeconds(Math.max(60, body.path("expires_in").asLong(3600) - 120));
        return token;
    }

    private JsonNode parse(String body) throws IOException {
        return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    private HttpResponse<String> sendRaw(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to PayPal", e);
        }
    }
}
