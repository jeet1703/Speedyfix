package com.speedyfixautosales.store.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.speedyfixautosales.store.common.UnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Suggested compatibility for a part, from Google's Gemini (the same key as the help chat). Gemini only proposes
 * makes, models, years and engines; each is then matched to eBay UK's vehicle catalogue (VehicleCatalogue), so every
 * row is a real vehicle the picker could have chosen. Suggestions that don't match are listed, not added. Gemini can
 * be wrong, so staff tick only what they've checked.
 */
@Service
public class FitmentSuggester {

    private static final Logger log = LoggerFactory.getLogger(FitmentSuggester.class);
    private static final String API = "https://generativelanguage.googleapis.com/v1beta/models/";
    static final int MAX_SUGGESTIONS = 25;

    /** What staff have entered about the part so far. */
    public record Part(String title, String partNumber, String engineCode, String brand, String category, String notes) {
    }

    /** One vehicle as Gemini put it. */
    record Suggestion(String make, String model, int yearFrom, int yearTo, String engineCode, Double engineLitres, String fuel) {
        String describe() {
            String engine = engineCode != null ? engineCode : engineLitres != null ? engineLitres + "L" : "";
            return (make + " " + model + " " + yearFrom + "–" + yearTo + " " + engine + (fuel != null ? " " + fuel : "")).trim();
        }
    }

    /** Catalogue rows for the suggestions that matched, and the ones that didn't, in words. */
    public record Result(List<VehicleRange> ranges, List<String> notMatched) {
    }

    private final VehicleCatalogue catalogue;
    private final String apiKey;
    private final List<String> models;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public FitmentSuggester(VehicleCatalogue catalogue, @Value("${gemini.api-key:}") String apiKey,
                            @Value("${gemini.models:gemini-3.5-flash,gemini-3.5-flash-lite}") List<String> models) {
        this.catalogue = catalogue;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.models = models.stream().map(String::trim).filter(m -> !m.isEmpty()).toList();
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !models.isEmpty() && catalogue.enabled();
    }

    public Result suggest(Part part) {
        if (!enabled()) {
            throw new UnavailableException("AI suggestions aren’t set up on this server.");
        }
        List<Suggestion> suggestions;
        try {
            suggestions = parse(ask(part));
        } catch (IOException e) {
            log.warn("Gemini fitment suggestion failed: {}", e.getMessage());
            throw new UnavailableException("The AI suggestion didn’t come back. Try again in a moment.");
        }
        List<String> makes = catalogue.makes();
        Map<String, VehicleRange> ranges = new LinkedHashMap<>();
        List<String> notMatched = new ArrayList<>();
        for (Suggestion s : suggestions) {
            List<VehicleRange> found = match(s, makes);
            if (found.isEmpty()) {
                notMatched.add(s.describe());
            }
            found.forEach(r -> ranges.putIfAbsent(r.id(), r));
        }
        return new Result(List.copyOf(ranges.values()), notMatched);
    }

    /** The catalogue's rows for one suggestion: its make and model, and the engines with its code (or size and fuel). */
    private List<VehicleRange> match(Suggestion s, List<String> makes) {
        Optional<String> make = makes.stream().filter(m -> key(m).equals(key(s.make()))).findFirst();
        if (make.isEmpty()) {
            return List.of();
        }
        Optional<String> model = bestModel(s.model(), catalogue.models(make.get()));
        if (model.isEmpty()) {
            return List.of();
        }
        List<VehicleCatalogue.EngineChoice> engines = catalogue.engineChoices(make.get(), model.get(), s.yearFrom(), s.yearTo());
        List<VehicleCatalogue.EngineChoice> chosen = engines.stream().filter(e -> sameCode(e, s.engineCode())).toList();
        if (chosen.isEmpty()) {
            chosen = engines.stream().filter(e -> sameSizeAndFuel(e, s)).toList();
        }
        return chosen.stream()
                .map(e -> VehicleRange.of(make.get(), model.get(), s.yearFrom(), s.yearTo(), e.name(), e.fuel()))
                .toList();
    }

    /** The catalogue model Gemini meant: the same name, else the longest catalogue name inside it ("Discovery 4" is "Discovery"). */
    static Optional<String> bestModel(String suggested, List<String> catalogueModels) {
        String want = key(suggested);
        return catalogueModels.stream().filter(m -> key(m).equals(want)).findFirst()
                .or(() -> catalogueModels.stream()
                        .filter(m -> key(m).length() >= 2 && want.startsWith(key(m)))
                        .max(Comparator.comparingInt(m -> key(m).length())));
    }

    static boolean sameCode(VehicleCatalogue.EngineChoice e, String code) {
        if (code == null || code.isBlank() || e.engineCode() == null) {
            return false;
        }
        String want = key(code);
        String have = key(e.engineCode());
        // eBay gives some engines no code ("--"): those never match a code.
        return want.length() >= 3 && have.length() >= 3 && (have.contains(want) || want.contains(have));
    }

    static boolean sameSizeAndFuel(VehicleCatalogue.EngineChoice e, Suggestion s) {
        if (s.engineLitres() == null) {
            return false;
        }
        Integer cc = VehicleRange.of("x", "x", 2000, 2000, e.name(), e.fuel()).cc();
        if (cc == null || Math.abs(cc - s.engineLitres() * 1000) > 60) {
            return false;
        }
        return s.fuel() == null || e.fuel() == null || e.fuel().equalsIgnoreCase(s.fuel());
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]", "");
    }

    private String ask(Part part) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", """
                You help a UK used car parts seller list which vehicles a part fits. From the part's details, list the
                UK-market cars it fits as factory-fitted: make, model, first and last model year, and the engine (its
                engine code where there is one, size in litres and fuel). Group years where the same engine runs on.
                Only list vehicles you're confident about from the part number and engine; leave out guesses. If the
                details aren't enough to tell, return an empty list.""");
        body.putArray("contents").addObject().put("role", "user").putArray("parts").addObject().put("text", describe(part));
        ObjectNode config = body.putObject("generationConfig");
        config.put("temperature", 0.2).put("maxOutputTokens", 4000).put("responseMimeType", "application/json");
        ObjectNode schema = config.putObject("responseSchema");
        schema.put("type", "OBJECT");
        ObjectNode vehicle = schema.putObject("properties").putObject("vehicles").put("type", "ARRAY").putObject("items");
        vehicle.put("type", "OBJECT");
        ObjectNode p = vehicle.putObject("properties");
        p.putObject("make").put("type", "STRING");
        p.putObject("model").put("type", "STRING");
        p.putObject("yearFrom").put("type", "INTEGER");
        p.putObject("yearTo").put("type", "INTEGER");
        p.putObject("engineCode").put("type", "STRING");
        p.putObject("engineLitres").put("type", "NUMBER");
        p.putObject("fuel").put("type", "STRING").putArray("enum").add("petrol").add("diesel").add("hybrid").add("electric");
        vehicle.putArray("required").add("make").add("model").add("yearFrom").add("yearTo");
        schema.putArray("required").add("vehicles");

        IOException last = null;
        for (String model : models) {
            HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create(API + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(40))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build());
            if (res.statusCode() == 200) {
                JsonNode parts = json.readTree(res.body()).path("candidates").path(0).path("content").path("parts");
                StringBuilder text = new StringBuilder();
                parts.forEach(piece -> {
                    if (!piece.path("thought").asBoolean(false)) {
                        text.append(piece.path("text").asText(""));
                    }
                });
                return text.toString();
            }
            last = new IOException(model + " HTTP " + res.statusCode() + ": " + res.body().substring(0, Math.min(160, res.body().length())));
            // Busy or out of free allowance: try the next model. Anything else won't be fixed by another model.
            if (res.statusCode() != 429 && res.statusCode() != 503 && res.statusCode() != 500 && res.statusCode() != 404) {
                break;
            }
        }
        throw last;
    }

    static String describe(Part part) {
        StringBuilder s = new StringBuilder("The part:\n");
        line(s, "Title", part.title());
        line(s, "Part number", part.partNumber());
        line(s, "Engine code", part.engineCode());
        line(s, "Brand", part.brand());
        line(s, "Category", part.category());
        line(s, "Notes", part.notes());
        return s.toString();
    }

    private static void line(StringBuilder s, String label, String value) {
        if (value != null && !value.isBlank()) {
            s.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    /** Gemini's JSON, with impossible years and blank names left out. */
    List<Suggestion> parse(String text) throws IOException {
        JsonNode list = json.readTree(text).path("vehicles");
        if (!list.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(((ArrayNode) list).spliterator(), false)
                .map(v -> new Suggestion(v.path("make").asText("").trim(), v.path("model").asText("").trim(),
                        v.path("yearFrom").asInt(0), v.path("yearTo").asInt(0), blankToNull(v.path("engineCode").asText("")),
                        v.path("engineLitres").isNumber() ? v.path("engineLitres").asDouble() : null, blankToNull(v.path("fuel").asText(""))))
                .filter(v -> !v.make().isEmpty() && !v.model().isEmpty())
                .filter(v -> v.yearFrom() >= 1950 && v.yearTo() >= v.yearFrom() && v.yearTo() <= 2100 && v.yearTo() - v.yearFrom() <= VehicleCatalogue.MAX_YEARS)
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
