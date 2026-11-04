package com.speedyfixautosales.store.ebay;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * eBay's Trading API, for the one thing the Browse API doesn't give: a listing's compatibility table (the vehicles
 * the seller ticked on eBay). It needs the Production keyset (EBAY_CLIENT_ID, EBAY_CLIENT_SECRET, EBAY_DEV_ID) and a
 * user token from the seller's own eBay account (EBAY_USER_TOKEN): the 18-month "Auth'n'Auth" token from the
 * developer portal's User Tokens page, or an OAuth user token.
 */
@Component
public class EbayTradingClient {

    private static final String API = "https://api.ebay.com/ws/api.dll";
    private static final String UK_SITE = "3";
    private static final String VERSION = "1193";

    private final String appId;
    private final String certId;
    private final String devId;
    private final String userToken;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public EbayTradingClient(@Value("${ebay.client-id:}") String appId, @Value("${ebay.client-secret:}") String certId,
                             @Value("${ebay.dev-id:}") String devId, @Value("${ebay.user-token:}") String userToken) {
        this.appId = appId.trim();
        this.certId = certId.trim();
        this.devId = devId.trim();
        this.userToken = userToken.trim();
    }

    public boolean enabled() {
        return !appId.isEmpty() && !certId.isEmpty() && !devId.isEmpty() && !userToken.isEmpty();
    }

    /**
     * The listing's compatibility rows, each as eBay names them ("Car Make", "Model", "Cars Year", "Engine",
     * "Variant", ...). Empty when the listing has no table.
     */
    public List<Map<String, String>> compatibility(String itemId) throws IOException {
        boolean oauth = userToken.startsWith("v^");
        String credentials = oauth ? "" : "<RequesterCredentials><eBayAuthToken>" + xml(userToken) + "</eBayAuthToken></RequesterCredentials>";
        String body = """
                <?xml version="1.0" encoding="utf-8"?>
                <GetItemRequest xmlns="urn:ebay:apis:eBLBaseComponents">%s
                  <ItemID>%s</ItemID>
                  <IncludeItemCompatibilityList>true</IncludeItemCompatibilityList>
                  <DetailLevel>ReturnAll</DetailLevel>
                  <OutputSelector>Item.ItemCompatibilityList</OutputSelector>
                </GetItemRequest>""".formatted(credentials, xml(itemId));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("X-EBAY-API-CALL-NAME", "GetItem")
                .header("X-EBAY-API-SITEID", UK_SITE)
                .header("X-EBAY-API-COMPATIBILITY-LEVEL", VERSION)
                .header("X-EBAY-API-APP-NAME", appId)
                .header("X-EBAY-API-DEV-NAME", devId)
                .header("X-EBAY-API-CERT-NAME", certId)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (oauth) {
            request.header("X-EBAY-API-IAF-TOKEN", userToken);
        }
        HttpResponse<byte[]> res;
        try {
            res = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to eBay", e);
        }
        return parse(res.body());
    }

    /** GetItem's reply: the rows, or an IOException with eBay's error when the call failed. */
    static List<Map<String, String>> parse(byte[] reply) throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setNamespaceAware(false);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(reply));
        } catch (Exception e) {
            throw new IOException("eBay sent a reply we couldn’t read.", e);
        }
        String ack = text(doc.getDocumentElement(), "Ack");
        if (!"Success".equals(ack) && !"Warning".equals(ack)) {
            NodeList errors = doc.getElementsByTagName("Errors");
            String message = errors.getLength() > 0 ? text((Element) errors.item(0), "LongMessage") : null;
            String code = errors.getLength() > 0 ? text((Element) errors.item(0), "ErrorCode") : null;
            throw new IOException("eBay error " + (code == null ? "" : code + ": ") + (message == null ? new String(reply, StandardCharsets.UTF_8).substring(0, Math.min(200, reply.length)) : message));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        NodeList compat = doc.getElementsByTagName("Compatibility");
        for (int i = 0; i < compat.getLength(); i++) {
            Map<String, String> row = new LinkedHashMap<>();
            NodeList pairs = ((Element) compat.item(i)).getElementsByTagName("NameValueList");
            for (int j = 0; j < pairs.getLength(); j++) {
                Element pair = (Element) pairs.item(j);
                String name = text(pair, "Name");
                String value = text(pair, "Value");
                if (name != null && value != null && !value.isBlank()) {
                    row.put(name.trim(), value.trim());
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static String text(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e.getTextContent();
            }
        }
        return null;
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
