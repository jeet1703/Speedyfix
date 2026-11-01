package com.speedyfixautosales.store.photo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listing photos in a Cloudflare R2 bucket, through its S3 API (requests signed with AWS Signature V4).
 * Switched on by R2_ACCOUNT_ID, R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY (secrets: set them in the environment);
 * R2_BUCKET defaults to speedyfix-images. The bucket can stay private: the server reads photos and serves them.
 */
@Component
public class R2Client {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String REGION = "auto";

    private final String accountId;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public R2Client(@Value("${photos.r2.account-id:}") String accountId,
                    @Value("${photos.r2.access-key-id:}") String accessKey,
                    @Value("${photos.r2.secret-access-key:}") String secretKey,
                    @Value("${photos.r2.bucket:speedyfix-images}") String bucket) {
        this.accountId = accountId.trim();
        this.accessKey = accessKey.trim();
        this.secretKey = secretKey.trim();
        this.bucket = bucket.trim();
    }

    public boolean enabled() {
        return !accountId.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty() && !bucket.isEmpty();
    }

    public void put(String key, byte[] data, String contentType) throws IOException {
        HttpResponse<byte[]> r = send("PUT", key, data, contentType);
        if (r.statusCode() / 100 != 2) {
            throw new IOException("R2 refused " + key + " (HTTP " + r.statusCode() + ")");
        }
    }

    /** The object's bytes; empty if it isn't in the bucket. */
    public Optional<byte[]> get(String key) throws IOException {
        HttpResponse<byte[]> r = send("GET", key, null, null);
        if (r.statusCode() == 404) {
            return Optional.empty();
        }
        if (r.statusCode() / 100 != 2) {
            throw new IOException("R2 couldn’t send " + key + " (HTTP " + r.statusCode() + ")");
        }
        return Optional.of(r.body());
    }

    private HttpResponse<byte[]> send(String method, String key, byte[] body, String contentType) throws IOException {
        if (!enabled()) {
            throw new IOException("R2 isn’t set up (R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY).");
        }
        String host = accountId + ".r2.cloudflarestorage.com";
        String path = "/" + encode(bucket) + "/" + Stream.of(key.split("/")).map(R2Client::encode).collect(Collectors.joining("/"));
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = hex(sha256(payload));
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String date = amzDate.substring(0, 8);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonical = method + "\n" + path + "\n\n"
                + "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n\n"
                + signedHeaders + "\n" + payloadHash;
        String scope = date + "/" + REGION + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        byte[] signingKey = hmac(hmac(hmac(hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date), REGION), "s3"), "aws4_request");
        String signature = hex(hmac(signingKey, toSign));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://" + host + path))
                .timeout(Duration.ofSeconds(30))
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .header("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                        + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(payload));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to R2", e);
        }
    }

    /** RFC 3986 encoding, as Signature V4 wants it. */
    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
