package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.common.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Hourly limits on the public actions that cost something: placing orders (which hold one-off parts), starting
 * payments, order lookups (so order numbers can't be guessed by trying many), part requests (stored with photos), plate lookups (DVSA's allowance), new baskets and sign-ins. Each has a
 * limit per client address and an overall one: addresses come through Render's proxy (X-Forwarded-For), which a
 * caller can vary, so the overall cap is the backstop. The numbers sit far above what a real shopper does.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_CLIENTS_TRACKED = 50_000;

    record Rule(String name, String pattern, int perClient, int overall, String message) {
    }

    static final List<Rule> RULES = List.of(
            new Rule("order", "/api/orders", 15, 300, "You’ve placed a lot of orders in the last hour. Call us and we’ll help."),
            new Rule("payment", "/api/orders/*/paypal", 30, 600, "Too many payment attempts. Wait a few minutes, or call us to pay by phone."),
            new Rule("order-lookup", "/api/orders/lookup", 20, 1_000, "Too many order lookups. Wait a few minutes, or call us with your order number."),
            new Rule("part-request", "/api/part-requests", 10, 200, "You’ve sent a lot of part requests in the last hour. Call or WhatsApp us instead."),
            new Rule("plate-lookup", "/api/vehicles/lookup", 60, 2_000, "Too many reg lookups in the last hour. Try again later, or search by part number."),
            new Rule("basket", "/api/baskets", 120, 3_000, "Too many new baskets. Wait a few minutes and try again."),
            new Rule("chat", "/api/chat", 40, 2_000, "You’ve asked a lot of questions in the last hour. WhatsApp or call us and we’ll help."),
            new Rule("sign-in", "/api/account/google", 30, 1_000, "Too many sign-in attempts. Wait a few minutes and try again."));

    private final AntPathMatcher paths = new AntPathMatcher();
    private final Map<String, Deque<Instant>> byClient = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> overall = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        for (Rule rule : RULES) {
            if (paths.match(rule.pattern(), path)) {
                take(rule, request.getRemoteAddr());
                break;
            }
        }
        return true;
    }

    private void take(Rule rule, String client) {
        Instant now = Instant.now();
        Deque<Instant> all = overall.computeIfAbsent(rule.name(), k -> new ArrayDeque<>());
        if (byClient.size() > MAX_CLIENTS_TRACKED) {
            byClient.clear();
        }
        Deque<Instant> mine = byClient.computeIfAbsent(rule.name() + "|" + client, k -> new ArrayDeque<>());
        synchronized (all) {
            prune(all, now);
            synchronized (mine) {
                prune(mine, now);
                if (mine.size() >= rule.perClient() || all.size() >= rule.overall()) {
                    throw new TooManyRequestsException(rule.message());
                }
                mine.addLast(now);
            }
            all.addLast(now);
        }
    }

    @Scheduled(fixedDelayString = "PT10M")
    void removeOld() {
        Instant now = Instant.now();
        byClient.values().forEach(d -> {
            synchronized (d) {
                prune(d, now);
            }
        });
        byClient.values().removeIf(Deque::isEmpty);
    }

    private static void prune(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.removeFirst();
        }
    }
}
