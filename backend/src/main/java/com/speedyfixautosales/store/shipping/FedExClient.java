package com.speedyfixautosales.store.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FedEx's Rate API (REST) with the shop's project keys from developer.fedex.com: FEDEX_API_KEY, FEDEX_SECRET_KEY and
 * the FedEx account number the rates are for, FEDEX_ACCOUNT_NUMBER. FEDEX_ENVIRONMENT is "sandbox" (the default) or
 * "live". Parcels are rated from FEDEX_SHIP_FROM_POSTCODE (the unit in Uxbridge) as drop-offs.
 */
@Component
public class FedExClient {

    /** One box: weight in kilograms, sides in centimetres (FedEx charges the larger of weight and size). */
    public record Parcel(BigDecimal weightKg, int lengthCm, int widthCm, int heightCm) {
    }

    /** Where it's going: postcode (may be blank abroad), town and ISO country code. */
    public record Destination(String postcode, String city, String country) {
        public static Destination uk(String postcode) {
            return new Destination(postcode, null, "GB");
        }

        boolean international() {
            return !"GB".equalsIgnoreCase(country);
        }
    }

    /** One kind of goods in an international shipment, for customs: value in pounds before VAT. */
    public record Commodity(String description, int quantity, BigDecimal weightKg, BigDecimal valueGbp) {
    }

    /** One FedEx service for the shipment: what FedEx charges the shop, before VAT, and when it should arrive. */
    public record Rate(String serviceType, String serviceName, BigDecimal chargeExVat, String currency, String arrives) {
    }

    private final String apiKey;
    private final String secretKey;
    private final String accountNumber;
    private final String fromPostcode;
    private final String fromCountry;
    private final String api;
    private final boolean live;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public FedExClient(@Value("${fedex.api-key:}") String apiKey, @Value("${fedex.secret-key:}") String secretKey,
                       @Value("${fedex.account-number:}") String accountNumber,
                       @Value("${fedex.environment:sandbox}") String environment,
                       @Value("${fedex.ship-from-postcode:UB8 2JP}") String fromPostcode,
                       @Value("${fedex.ship-from-country:GB}") String fromCountry) {
        this.apiKey = apiKey.trim();
        this.secretKey = secretKey.trim();
        this.accountNumber = accountNumber.trim();
        this.fromPostcode = fromPostcode.trim();
        this.fromCountry = fromCountry.trim().toUpperCase();
        this.live = "live".equalsIgnoreCase(environment.trim());
        this.api = live ? "https://apis.fedex.com" : "https://apis-sandbox.fedex.com";
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !secretKey.isEmpty() && !accountNumber.isEmpty();
    }

    public boolean live() {
        return live;
    }

    /**
     * Every service FedEx offers for the parcels to the destination, cheapest first. Abroad, FedEx needs the goods for
     * customs (what they are and their value).
     */
    public List<Rate> rates(Destination to, List<Parcel> parcels, List<Commodity> goods) throws IOException {
        JsonNode res = send("/rate/v1/rates/quotes", request(to, parcels, goods).toString());
        return parse(res);
    }

    ObjectNode request(Destination dest, List<Parcel> parcels, List<Commodity> goods) {
        ObjectNode body = json.createObjectNode();
        body.putObject("accountNumber").put("value", accountNumber);
        body.putObject("rateRequestControlParameters").put("returnTransitTimes", true);
        ObjectNode shipment = body.putObject("requestedShipment");
        shipment.putObject("shipper").putObject("address").put("postalCode", fromPostcode).put("countryCode", fromCountry);
        ObjectNode to = shipment.putObject("recipient").putObject("address");
        // FedEx wants the field even where the country has no postcodes (empty then).
        to.put("postalCode", dest.postcode() == null ? "" : dest.postcode());
        if (dest.city() != null && !dest.city().isBlank()) {
            to.put("city", dest.city());
        }
        to.put("countryCode", dest.country().toUpperCase());
        to.put("residential", true);
        shipment.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        shipment.put("preferredCurrency", "GBP");
        shipment.putArray("rateRequestType").add("ACCOUNT").add("LIST");
        shipment.put("totalPackageCount", parcels.size());
        ArrayNode items = shipment.putArray("requestedPackageLineItems");
        for (Parcel p : parcels) {
            ObjectNode item = items.addObject();
            item.putObject("weight").put("units", "KG").put("value", p.weightKg());
            item.putObject("dimensions").put("length", p.lengthCm()).put("width", p.widthCm()).put("height", p.heightCm()).put("units", "CM");
        }
        if (dest.international()) {
            // The quote is for the shop's account; who pays import duty is set when the parcel is booked (the buyer).
            ObjectNode customs = shipment.putObject("customsClearanceDetail");
            customs.putObject("dutiesPayment").put("paymentType", "SENDER");
            ArrayNode commodities = customs.putArray("commodities");
            for (Commodity c : goods) {
                ObjectNode item = commodities.addObject();
                item.put("description", c.description().length() > 100 ? c.description().substring(0, 100) : c.description());
                item.put("quantity", c.quantity()).put("quantityUnits", "PCS");
                item.putObject("weight").put("units", "KG").put("value", c.weightKg());
                item.putObject("customsValue").put("amount", c.valueGbp()).put("currency", "GBP");
            }
        }
        return body;
    }

    /**
     * FedEx gives each service one or more prices (the shop's account rate, the list rate, a converted one). This takes
     * the account rate in pounds where there is one, and leaves out any VAT FedEx included, so the shop adds its own.
     */
    static List<Rate> parse(JsonNode res) {
        List<Rate> rates = new ArrayList<>();
        for (JsonNode reply : res.path("output").path("rateReplyDetails")) {
            JsonNode chosen = null;
            for (JsonNode d : reply.path("ratedShipmentDetails")) {
                boolean gbp = "GBP".equalsIgnoreCase(d.path("currency").asText(d.path("shipmentRateDetail").path("currency").asText()));
                if (gbp && (chosen == null || "ACCOUNT".equals(d.path("rateType").asText()))) {
                    chosen = d;
                }
            }
            if (chosen == null || !chosen.path("totalNetCharge").isNumber()) {
                continue;
            }
            // totalNetFedExCharge is the charge before taxes; otherwise take the taxes (UK VAT) off the net charge.
            BigDecimal exVat;
            if (chosen.path("totalNetFedExCharge").isNumber()) {
                exVat = chosen.path("totalNetFedExCharge").decimalValue();
            } else {
                BigDecimal taxes = BigDecimal.ZERO;
                for (JsonNode t : chosen.path("shipmentRateDetail").path("taxes")) {
                    taxes = taxes.add(t.path("amount").decimalValue());
                }
                exVat = chosen.path("totalNetCharge").decimalValue().subtract(taxes);
            }
            JsonNode commit = reply.path("commit");
            String arrives = commit.path("dateDetail").path("dayFormat").asText(commit.path("dateDetails").path("dayFormat").asText(
                    reply.path("operationalDetail").path("deliveryDate").asText("")));
            String type = reply.path("serviceType").asText("");
            rates.add(new Rate(type, reply.path("serviceName").asText(type), exVat, "GBP", arrives.isBlank() ? null : arrives));
        }
        rates.sort((a, b) -> a.chargeExVat().compareTo(b.chargeExVat()));
        return rates;
    }

    /** FedEx refused the request; the message carries FedEx's error code (like SERVICE.UNAVAILABLE.ERROR). */
    public static class FedExException extends IOException {
        private static final long serialVersionUID = 1L;

        FedExException(String message) {
            super(message);
        }
    }

    private JsonNode send(String path, String body) throws IOException {
        Reply res = sendRaw(HttpRequest.newBuilder(URI.create(api + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .header("X-locale", "en_GB")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        if (res.statusCode() / 100 != 2) {
            JsonNode err = parse(res.body());
            JsonNode first = err.path("errors").path(0);
            throw new FedExException(first.path("code").asText("HTTP " + res.statusCode()) + ": "
                    + first.path("message").asText(res.body().substring(0, Math.min(200, res.body().length()))));
        }
        return parse(res.body());
    }

    private synchronized String token() throws IOException {
        if (!enabled()) {
            throw new IOException("FedEx isn’t set up (FEDEX_API_KEY, FEDEX_SECRET_KEY, FEDEX_ACCOUNT_NUMBER).");
        }
        if (token != null && Instant.now().isBefore(tokenExpires)) {
            return token;
        }
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secretKey, StandardCharsets.UTF_8);
        Reply res = sendRaw(HttpRequest.newBuilder(URI.create(api + "/oauth/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        if (res.statusCode() != 200) {
            throw new IOException("FedEx refused the keys (HTTP " + res.statusCode() + "). Check FEDEX_API_KEY, FEDEX_SECRET_KEY and FEDEX_ENVIRONMENT.");
        }
        JsonNode body = parse(res.body());
        token = body.path("access_token").asText();
        tokenExpires = Instant.now().plusSeconds(Math.max(60, body.path("expires_in").asLong(3600) - 120));
        return token;
    }

    private JsonNode parse(String body) throws IOException {
        return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    /** FedEx's answer: its status, and its body as text (FedEx sometimes gzips it, errors included). */
    private record Reply(int statusCode, String body) {
    }

    private Reply sendRaw(HttpRequest request) throws IOException {
        try {
            HttpResponse<byte[]> res = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = res.body() == null ? new byte[0] : res.body();
            if (body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b) {
                try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
                    body = in.readAllBytes();
                }
            }
            return new Reply(res.statusCode(), new String(body, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to FedEx", e);
        }
    }
}
