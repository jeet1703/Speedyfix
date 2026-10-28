package com.speedyfixautosales.store.vehicle;

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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DVSA's MOT History API (free, official): vehicle details by registration for vehicles registered in Great Britain
 * and Northern Ireland. Signs in with Microsoft Entra client credentials (token cached for its lifetime) and sends the
 * API key on every call. Credentials come from DVSA's registration email; see INTEGRATIONS.md.
 */
@Component
public class DvsaClient {

    private static final Logger log = LoggerFactory.getLogger(DvsaClient.class);
    private static final String API = "https://history.mot.api.gov.uk/v1/trade/vehicles/registration/";

    /** What DVSA knows about a vehicle. engineSizeCc is null for vehicles too new for an MOT. */
    public record DvsaVehicle(String make, String model, String fuelType, Integer engineSizeCc, Integer year,
                              String colour) {
    }

    private final String clientId;
    private final String clientSecret;
    private final String apiKey;
    private final String tokenUrl;
    private final String scope;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public DvsaClient(@Value("${dvsa.client-id:}") String clientId, @Value("${dvsa.client-secret:}") String clientSecret,
                      @Value("${dvsa.api-key:}") String apiKey, @Value("${dvsa.token-url:}") String tokenUrl,
                      @Value("${dvsa.scope:https://tapi.dvsa.gov.uk/.default}") String scope) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.apiKey = apiKey.trim();
        this.tokenUrl = tokenUrl.trim();
        this.scope = scope.trim();
    }

    /** True once all four DVSA credentials are set. */
    public boolean enabled() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !apiKey.isEmpty() && !tokenUrl.isEmpty();
    }

    /**
     * @param registration normalised (upper case, no spaces)
     * @return empty when DVSA doesn't know the plate
     * @throws IOException when DVSA can't be reached or refuses the credentials
     */
    public Optional<DvsaVehicle> find(String registration) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + URLEncoder.encode(registration, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token())
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> res = send(request);
        if (res.statusCode() == 401) {
            tokenExpires = Instant.EPOCH;
            res = send(HttpRequest.newBuilder(request.uri()).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token()).header("X-API-Key", apiKey)
                    .header("Accept", "application/json").GET().build());
        }
        if (res.statusCode() == 404) {
            return Optional.empty();
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("DVSA returned HTTP " + res.statusCode());
        }
        return Optional.of(parse(json.readTree(res.body())));
    }

    /** Works for both shapes DVSA returns: vehicles with MOT history, and new vehicles that haven't had one yet. */
    static DvsaVehicle parse(JsonNode v) {
        Integer cc = null;
        String size = text(v, "engineSize");
        if (size != null && size.replaceAll("\\D", "").length() > 0) {
            cc = Integer.parseInt(size.replaceAll("\\D", ""));
        }
        Integer year = null;
        for (String field : new String[] {"firstUsedDate", "registrationDate", "manufactureDate"}) {
            String date = text(v, field);
            if (date != null && date.length() >= 4 && date.substring(0, 4).matches("\\d{4}")) {
                year = Integer.parseInt(date.substring(0, 4));
                break;
            }
        }
        if (year == null && v.hasNonNull("manufactureYear")) {
            year = v.path("manufactureYear").asInt();
        }
        return new DvsaVehicle(text(v, "make"), text(v, "model"), text(v, "fuelType"), cc, year, text(v, "primaryColour"));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank() ? node.path(field).asText().trim() : null;
    }

    /** The access token, fetched again shortly before it expires (DVSA tokens last up to an hour). */
    private synchronized String token() throws IOException {
        if (token != null && Instant.now().isBefore(tokenExpires)) {
            return token;
        }
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
        HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        if (res.statusCode() != 200) {
            log.warn("DVSA sign-in refused (HTTP {}). Check DVSA_CLIENT_ID, DVSA_CLIENT_SECRET and DVSA_TOKEN_URL.", res.statusCode());
            throw new IOException("DVSA sign-in refused");
        }
        JsonNode body = json.readTree(res.body());
        token = body.path("access_token").asText();
        tokenExpires = Instant.now().plusSeconds(Math.max(60, body.path("expires_in").asLong(1200) - 60));
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
}
