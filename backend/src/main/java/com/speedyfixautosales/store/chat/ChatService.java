package com.speedyfixautosales.store.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.catalog.ProductView;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.shipping.DeliveryQuotes;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The help chat's AI answers, from Google's Gemini with tools that read our own data: the live stock search, a reg
 * check (DVSA plus our fitting parts) and the shop's delivery, returns and contact details. Gemini writes the words;
 * every fact comes from a tool. Switched on by GEMINI_API_KEY; GEMINI_MODELS lists the models to try in order.
 *
 * Privacy: Gemini never sees an email address, an order number or a registration. They're replaced with placeholders
 * before anything is sent ("[REG_1]"), and when Gemini asks to check "[REG_1]" this server looks up the real one.
 * Order tracking stays in the chat's own guided flow and never comes here.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String API = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_TOOL_ROUNDS = 4;
    static final int MAX_TURNS = 12;
    static final int MAX_TEXT = 500;

    static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    static final Pattern ORDER_REF = Pattern.compile("\\bSF-?[A-Z0-9]{8}\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern REG = Pattern.compile(
            "\\b([A-Z]{2}\\d{2}\\s?[A-Z]{3}|[A-Z]\\d{1,3}\\s?[A-Z]{3}|[A-Z]{3}\\s?\\d{1,3}[A-Z])\\b", Pattern.CASE_INSENSITIVE);

    /** One message in the conversation; role is "user" or "assistant". */
    public record Turn(String role, String text) {
    }

    /** The answer, and the parts and vehicle its tools found (the chat shows the parts as cards). */
    public record Reply(String text, List<ProductView> parts, Vehicle vehicle) {
    }

    private final String apiKey;
    private final List<String> models;
    private final CatalogService catalog;
    private final VehicleService vehicles;
    private final SiteProperties site;
    private final DeliveryQuotes quotes;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public ChatService(@Value("${gemini.api-key:}") String apiKey, @Value("${gemini.models:gemini-3.5-flash,gemini-3.5-flash-lite}") List<String> models,
                       CatalogService catalog, VehicleService vehicles, SiteProperties site, DeliveryQuotes quotes) {
        this.apiKey = apiKey.trim();
        this.models = models.stream().map(String::trim).filter(m -> !m.isEmpty()).toList();
        this.catalog = catalog;
        this.vehicles = vehicles;
        this.site = site;
        this.quotes = quotes;
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !models.isEmpty();
    }

    /** @param vehicleId the shopper's vehicle if they've given a reg already (parts are then checked against it) */
    public Reply reply(List<Turn> conversation, String vehicleId) {
        if (!enabled()) {
            throw new UnavailableException("The AI assistant isn’t switched on.");
        }
        Masked masked = mask(conversation);
        Session session = new Session(masked.regs(), vehicles.findById(vehicleId).orElse(null));
        ArrayNode contents = json.createArrayNode();
        for (Turn t : masked.turns()) {
            ObjectNode c = contents.addObject();
            c.put("role", "assistant".equals(t.role()) ? "model" : "user");
            c.putArray("parts").addObject().put("text", t.text());
        }
        try {
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                JsonNode candidate = generate(contents).path("candidates").path(0).path("content");
                List<JsonNode> calls = new ArrayList<>();
                StringBuilder text = new StringBuilder();
                for (JsonNode part : candidate.path("parts")) {
                    if (part.has("functionCall")) {
                        calls.add(part.path("functionCall"));
                    } else if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
                        text.append(part.path("text").asText());
                    }
                }
                if (calls.isEmpty()) {
                    String answer = text.toString().trim();
                    if (answer.isEmpty()) {
                        throw new IOException("Gemini sent no answer");
                    }
                    return new Reply(unmask(answer, masked.regs()), cardsFor(answer, session), session.vehicle);
                }
                // The model's turn goes back exactly as sent (newer models need their thought signatures returned).
                contents.add(candidate);
                ObjectNode results = contents.addObject();
                results.put("role", "user");
                ArrayNode parts = results.putArray("parts");
                for (JsonNode call : calls) {
                    ObjectNode response = parts.addObject().putObject("functionResponse");
                    response.put("name", call.path("name").asText());
                    response.set("response", runTool(call.path("name").asText(), call.path("args"), session));
                }
            }
            throw new IOException("Gemini kept calling tools");
        } catch (IOException e) {
            log.warn("AI chat failed: {}", e.getMessage());
            throw new UnavailableException("The AI assistant is busy right now.");
        }
    }

    /** What one conversation's tools have found so far. */
    static final class Session {
        final Map<String, String> regs;
        Vehicle vehicle;
        /** Every part the tools found, in the order found; the last search's first. */
        final Map<String, ProductView> parts = new LinkedHashMap<>();
        List<ProductView> lastSearch = List.of();

        Session(Map<String, String> regs, Vehicle vehicle) {
            this.regs = regs;
            this.vehicle = vehicle;
        }
    }

    private JsonNode runTool(String name, JsonNode args, Session s) {
        ObjectNode out = json.createObjectNode();
        switch (name) {
            case "searchParts" -> {
                String query = args.path("query").asText("").trim();
                String sort = args.path("sort").asText("relevance");
                BigDecimal maxPrice = args.hasNonNull("maxPriceGbp") ? new BigDecimal(args.path("maxPriceGbp").asText("0")) : null;
                // Sorting or a price cap looks at every match, so "cheapest" really is the cheapest we have.
                boolean wide = !"relevance".equals(sort) || maxPrice != null;
                List<ProductView> found = query.isEmpty() ? List.of()
                        : catalog.search(query, wide ? 500 : 8, s.vehicle == null ? null : s.vehicle.id());
                int total = found.size();
                if (maxPrice != null) {
                    found = found.stream().filter(p -> p.price().compareTo(maxPrice) <= 0).toList();
                }
                if ("price_low".equals(sort)) {
                    found = found.stream().sorted(Comparator.comparing(ProductView::price)).toList();
                } else if ("price_high".equals(sort)) {
                    found = found.stream().sorted(Comparator.comparing(ProductView::price).reversed()).toList();
                }
                found = found.stream().limit(8).toList();
                ArrayNode list = out.putArray("parts");
                for (ProductView p : found) {
                    ObjectNode o = list.addObject();
                    o.put("name", p.name());
                    o.put("priceGbpIncVat", p.price());
                    if (p.originalPrice() != null) {
                        o.put("wasGbp", p.originalPrice());
                        o.put("salePercent", p.salePercent());
                    }
                    o.put("condition", p.condition());
                    o.put("inStock", p.stock());
                    if (p.reference() != null) {
                        o.put("reference", p.reference());
                    }
                    if (s.vehicle != null) {
                        o.put("fitsTheirVehicle", p.fit().name());
                    }
                }
                found.forEach(p -> s.parts.putIfAbsent(p.id(), p));
                s.lastSearch = found;
                out.put("count", found.size());
                out.put("matchesBeforePriceFilter", total);
                if (found.isEmpty()) {
                    out.put("note", "Nothing in stock matches. Suggest Request a Part or WhatsApp; the team can often source parts.");
                }
            }
            case "checkRegistration" -> {
                String token = args.path("registration").asText("").trim();
                String reg = s.regs.getOrDefault(token, token);
                try {
                    Vehicle v = vehicles.lookUp(reg);
                    s.vehicle = v;
                    out.put("vehicle", (v.year() == null ? "" : v.year() + " ") + v.make() + " " + v.model() + ", " + v.engine());
                    out.put("partsThatMayFit", catalog.countFitting(v.id()));
                } catch (NotFoundException | IllegalArgumentException e) {
                    out.put("error", "That registration wasn't found.");
                }
            }
            case "shopInfo" -> {
                out.put("delivery", "UK delivery usually within 7 working days with " + site.couriers()
                        + (quotes.live()
                            ? ". Parcels go by FedEx, priced from the postcode at checkout (the price and delivery date of each service show there); the cheapest service is free on orders of £100 or more."
                            : ". Parcels £9.95, free on orders of £100 or more.")
                        + (quotes.live()
                            ? " Engines and gearboxes go by FedEx Freight, also priced at checkout; free delivery doesn't apply to them."
                                + " Manual delivery is also offered at checkout: pay for the parts, then get a quote from another courier."
                            : " Engines and gearboxes go by pallet, £79 per order.")
                        + " International shipping by UK Pallets or FedEx at extra cost, 3 to 10 working days after dispatch;"
                        + " the buyer pays any import VAT or duties.");
                out.put("returns", site.returns());
                out.put("warranty", site.warranty() + " warranty from delivery, covering the part only (not labour or fitting).");
                out.put("payment", "PayPal, or debit or credit card through PayPal. Prices include VAT.");
                out.put("contact", "WhatsApp or phone " + site.phone() + ", email " + site.email() + ". Address: " + site.address() + ".");
                out.put("company", site.companyName() + ", company no. " + site.companyNumber() + ", VAT " + site.vatNumber() + ".");
                out.put("orderTracking", "Customers track orders with the chat's 'Where's my order?' button (order number and email).");
            }
            default -> out.put("error", "No such tool.");
        }
        return out;
    }

    private JsonNode generate(ArrayNode contents) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", instructions());
        body.set("contents", contents);
        ArrayNode tools = body.putArray("tools").addObject().putArray("functionDeclarations");
        ObjectNode search = tool(tools, "searchParts", "Searches the shop's live stock (up to 8 results). Use for any question about a part, a part number or what's available.",
                "query", "What to search for: a part number, or the part and vehicle, like 'Qashqai turbo' or 'gearbox'.");
        ObjectNode props = (ObjectNode) search.path("parameters").path("properties");
        props.putObject("sort").put("type", "string").put("description",
                "relevance (default), price_low for the cheapest first, or price_high for the dearest first. Use price_low for 'cheapest'.")
                .putArray("enum").add("relevance").add("price_low").add("price_high");
        props.putObject("maxPriceGbp").put("type", "number").put("description", "Only parts at or under this price, in pounds, for 'under £500' questions.");
        tool(tools, "checkRegistration", "Looks up a UK registration (shown to you as a placeholder like [REG_1]) and says how many parts may fit.",
                "registration", "The registration placeholder exactly as it appears, like [REG_1].");
        tools.addObject().put("name", "shopInfo").put("description", "The shop's delivery, returns, warranty, payment and contact details.");
        body.putObject("generationConfig").put("temperature", 0.4).put("maxOutputTokens", 600);

        IOException last = null;
        for (String model : models) {
            HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create(API + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(25))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build());
            if (res.statusCode() == 200) {
                return json.readTree(res.body());
            }
            last = new IOException(model + " HTTP " + res.statusCode() + ": " + res.body().substring(0, Math.min(160, res.body().length())));
            // Busy or out of free allowance: try the next model. Anything else won't be fixed by another model.
            if (res.statusCode() != 429 && res.statusCode() != 503 && res.statusCode() != 500 && res.statusCode() != 404) {
                break;
            }
        }
        throw last;
    }

    private static ObjectNode tool(ArrayNode tools, String name, String description, String param, String paramDescription) {
        ObjectNode t = tools.addObject();
        t.put("name", name).put("description", description);
        ObjectNode params = t.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties").putObject(param).put("type", "string").put("description", paramDescription);
        params.putArray("required").add(param);
        return t;
    }

    /**
     * The parts to show as cards: those the answer names (by price, part number or name), else the top of the last
     * search. At most three, so the cards match what was said.
     */
    static List<ProductView> cardsFor(String answer, Session s) {
        String a = answer.toLowerCase(java.util.Locale.UK);
        List<ProductView> named = s.parts.values().stream().filter(p -> mentions(a, p)).limit(3).toList();
        if (!named.isEmpty()) {
            return named;
        }
        return s.lastSearch.stream().limit(3).toList();
    }

    private static boolean mentions(String answer, ProductView p) {
        String price = p.price().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        // The whole price ("£249.99" names that part, not the one at £49.99), with or without thousands commas.
        String withCommas = String.format(java.util.Locale.UK, "%,.2f", p.price());
        if (Pattern.compile("(?<![\\d.,])(" + Pattern.quote(price) + "|" + Pattern.quote(withCommas) + ")(?!\\d)").matcher(answer).find()) {
            return true;
        }
        if (p.reference() != null) {
            String ref = p.reference().replaceFirst("^(Part no\\.|Engine code) ", "").toLowerCase(java.util.Locale.UK);
            if (ref.length() >= 5 && answer.contains(ref)) {
                return true;
            }
        }
        // The first several words of the name, as an answer usually quotes them.
        String[] words = p.name().toLowerCase(java.util.Locale.UK).split("\\s+");
        return words.length >= 4 && answer.contains(String.join(" ", java.util.Arrays.copyOfRange(words, 0, 4)));
    }

    private String instructions() {
        return """
                You are the online assistant for %s, a UK seller of used, remanufactured and new car parts (engines, gearboxes,
                turbos, injectors, ECUs, body panels and more), based in %s. You help shoppers on the shop's website.

                Rules:
                - Only help with this shop: finding parts, whether a part fits a vehicle, prices, stock, delivery, returns,
                  warranty, payment and contacting the team. Politely decline anything else in one sentence.
                - Never state a part, price, stock level, fitment or policy from memory. Use the tools: searchParts for parts,
                  checkRegistration for a reg, shopInfo for delivery, returns, warranty, payment and contact. If a tool doesn't
                  give you the answer, say you don't know and suggest WhatsApp or Request a Part.
                - Prices are in pounds and include VAT. During a sale, give the sale price and say it's on sale.
                - The chat shows the parts you mention as cards with links, so name at most the top two or three (with their
                  price), don't paste long lists, and don't write links.
                - For "cheapest", "dearest" or a budget, use searchParts with sort or maxPriceGbp; don't guess.
                - For fitment, the safest check is the part number on the old part, or sending the reg or VIN to the team.
                - Never ask for, or repeat, email addresses, payment details or order numbers. For an order, tell them to use
                  the "Where's my order?" button.
                - Registrations appear as placeholders like [REG_1]; pass them to checkRegistration unchanged.
                - Write in friendly, plain UK English. Keep answers short: two to four sentences. Use **bold** for a key fact
                  or two, and no other formatting.
                """.formatted(site.companyName(), site.shipsFrom());
    }

    /** The conversation with emails, order numbers and registrations swapped for placeholders, and how to swap regs back. */
    record Masked(List<Turn> turns, Map<String, String> regs) {
    }

    static Masked mask(List<Turn> conversation) {
        Map<String, String> regs = new LinkedHashMap<>();
        List<Turn> out = new ArrayList<>();
        List<Turn> recent = conversation.size() > MAX_TURNS ? conversation.subList(conversation.size() - MAX_TURNS, conversation.size()) : conversation;
        for (Turn t : recent) {
            String text = t.text() == null ? "" : t.text();
            text = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
            text = EMAIL.matcher(text).replaceAll("[email removed]");
            text = ORDER_REF.matcher(text).replaceAll("[order number removed]");
            Matcher m = REG.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String reg = m.group(1).toUpperCase().replaceAll("\\s", "");
                String token = regs.entrySet().stream().filter(e -> e.getValue().equals(reg)).map(Map.Entry::getKey).findFirst()
                        .orElseGet(() -> {
                            String k = "[REG_" + (regs.size() + 1) + "]";
                            regs.put(k, reg);
                            return k;
                        });
                m.appendReplacement(sb, Matcher.quoteReplacement(token));
            }
            m.appendTail(sb);
            out.add(new Turn("assistant".equals(t.role()) ? "assistant" : "user", sb.toString()));
        }
        // A conversation has to start with the shopper, and end with them.
        while (!out.isEmpty() && "assistant".equals(out.get(0).role())) {
            out.remove(0);
        }
        return new Masked(out, regs);
    }

    /** Puts the shopper's own registration back where the model wrote its placeholder. */
    static String unmask(String text, Map<String, String> regs) {
        String out = text;
        for (Map.Entry<String, String> e : regs.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    Optional<String> firstModel() {
        return models.stream().findFirst();
    }
}
