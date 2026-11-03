package com.speedyfixautosales.store.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends email through Resend's HTTP API (resend.com), with RESEND_API_KEY. An API rather than SMTP because Render's
 * free servers block outgoing SMTP. Resend verifies the whole domain (the SPF and DKIM records it gives, added to the
 * domain's DNS), so any address on it can send: MAIL_FROM for orders, MAIL_ACCOUNT_FROM for sign-up codes. No mailbox
 * is needed behind them; replies go to MAIL_REPLY_TO. Without a key, nothing is sent.
 */
@Component
public class EmailSender {

    private static final URI API = URI.create("https://api.resend.com/emails");

    public record Attachment(String fileName, byte[] content) {
    }

    /** from is null for MAIL_FROM; any address on the verified domain works (account@, orders@...). */
    public record Email(String to, String subject, String html, String text, List<Attachment> attachments, String from) {

        public Email(String to, String subject, String html, String text, List<Attachment> attachments) {
            this(to, subject, html, text, attachments, null);
        }
    }

    /** Resend refused the email. permanent: sending it again won't help (an address it won't deliver to, say). */
    public static class SendFailed extends IOException {
        private static final long serialVersionUID = 1L;
        private final boolean permanent;

        SendFailed(String message, boolean permanent) {
            super(message);
            this.permanent = permanent;
        }

        public boolean permanent() {
            return permanent;
        }
    }

    private final String apiKey;
    private final String from;
    private final String replyTo;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public EmailSender(@Value("${mail.resend-api-key:}") String apiKey, @Value("${mail.from:}") String from,
                       @Value("${mail.reply-to:}") String replyTo) {
        this.apiKey = apiKey.trim();
        this.from = from.trim();
        this.replyTo = replyTo.trim();
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !from.isEmpty();
    }

    /**
     * Sends the email and returns Resend's id for it. The idempotency key makes Resend send it once even if this is
     * called again after a lost answer.
     */
    public String send(Email email, String idempotencyKey) throws IOException {
        if (!enabled()) {
            throw new SendFailed("Email isn’t set up (RESEND_API_KEY, MAIL_FROM).", false);
        }
        HttpResponse<String> res;
        try {
            res = http.send(HttpRequest.newBuilder(API)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body(email).toString()))
                    .build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending email", e);
        }
        JsonNode reply = res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
        if (res.statusCode() / 100 != 2) {
            String message = "HTTP " + res.statusCode() + ": " + reply.path("message").asText(reply.path("name").asText(""));
            // 400 and 422: Resend won't take this email as it is. Others (bad key, rate limit, outage) may pass later.
            throw new SendFailed(message, res.statusCode() == 400 || res.statusCode() == 422);
        }
        return reply.path("id").asText("");
    }

    ObjectNode body(Email email) {
        ObjectNode body = json.createObjectNode();
        body.put("from", email.from() == null || email.from().isBlank() ? from : email.from());
        body.putArray("to").add(email.to());
        if (!replyTo.isEmpty()) {
            body.put("reply_to", replyTo);
        }
        body.put("subject", email.subject());
        body.put("html", email.html());
        body.put("text", email.text());
        if (!email.attachments().isEmpty()) {
            ArrayNode files = body.putArray("attachments");
            for (Attachment a : email.attachments()) {
                files.addObject().put("filename", a.fileName()).put("content", Base64.getEncoder().encodeToString(a.content()));
            }
        }
        return body;
    }
}
