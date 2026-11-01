package com.speedyfixautosales.store.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.PayPalClient;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Paying for an order with PayPal, with PayPal replaced by a stand-in that "captures" whatever amount a test sets. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PayPalCheckoutApiTest {

    /** The amount the fake PayPal reports as captured, per PayPal order; the order's own total unless a test changes it. */
    static final Map<String, BigDecimal> CAPTURED = new HashMap<>();

    @TestConfiguration
    static class FakePayPal {
        @Bean
        @Primary
        PayPalClient fakePayPal() {
            return new PayPalClient("test-client", "test-secret", "sandbox") {
                private int next = 1;

                @Override
                public synchronized String createOrder(Order order, String brandName) {
                    String id = "PP-" + next++;
                    CAPTURED.putIfAbsent(id, order.total());
                    REFERENCES.put(id, order.reference());
                    return id;
                }

                @Override
                public Capture capture(String paypalOrderId) {
                    return new Capture("COMPLETED", "CAP-" + paypalOrderId, CAPTURED.get(paypalOrderId), "GBP", REFERENCES.get(paypalOrderId));
                }
            };
        }
    }

    static final Map<String, String> REFERENCES = new HashMap<>();

    @Autowired
    private MockMvc mvc;

    private String placeOrder() throws Exception {
        String basketId = JsonPath.read(mvc.perform(post("/api/baskets")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/baskets/" + basketId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"gm-sensor\",\"quantity\":1}")).andExpect(status().isOk());
        String body = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"basketId":"%s","name":"Pat Payer","email":"pat@example.com","phone":"07700 900123",
                         "line1":"1 Example Street","town":"London","postcode":"sw1a1aa"}""".formatted(basketId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.reference");
    }

    private String startPayPal(String reference) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/orders/" + reference + "/paypal")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.paypalOrderId");
    }

    private String approved(String paypalOrderId) {
        return "{\"paypalOrderId\":\"" + paypalOrderId + "\"}";
    }

    @Test
    void orderLookupNeedsTheEmailTheOrderWasPlacedWith() throws Exception {
        String reference = placeOrder();
        mvc.perform(post("/api/orders/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"" + reference.toLowerCase() + "\",\"email\":\" PAT@example.com \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference));
        mvc.perform(post("/api/orders/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"" + reference + "\",\"email\":\"someone@example.com\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"SF-NOTREAL1\",\"email\":\"pat@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void siteTellsTheBrowserPayPalIsOnAndHowLongPartsAreHeld() throws Exception {
        mvc.perform(get("/api/site"))
                .andExpect(jsonPath("$.paypalClientId").value("test-client"))
                .andExpect(jsonPath("$.unpaidHoldMinutes").value(120));
    }

    @Test
    void approvedPaymentMarksTheOrderPaidAndCapturingAgainChangesNothing() throws Exception {
        String reference = placeOrder();
        String paypalOrder = startPayPal(reference);

        mvc.perform(post("/api/orders/" + reference + "/paypal/capture").contentType(MediaType.APPLICATION_JSON).content(approved(paypalOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_DISPATCH"))
                .andExpect(jsonPath("$.payment.captureId").value("CAP-" + paypalOrder))
                .andExpect(jsonPath("$.payment.paidAt").exists());
        mvc.perform(post("/api/orders/" + reference + "/paypal/capture").contentType(MediaType.APPLICATION_JSON).content(approved(paypalOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_DISPATCH"));
        // A paid order can't start another payment.
        mvc.perform(post("/api/orders/" + reference + "/paypal")).andExpect(status().isConflict());
    }

    @Test
    void captureForTheWrongAmountDoesNotMarkTheOrderPaid() throws Exception {
        String reference = placeOrder();
        String paypalOrder = startPayPal(reference);
        CAPTURED.put(paypalOrder, new BigDecimal("0.01"));

        mvc.perform(post("/api/orders/" + reference + "/paypal/capture").contentType(MediaType.APPLICATION_JSON).content(approved(paypalOrder)))
                .andExpect(status().isPaymentRequired());
        mvc.perform(get("/api/orders/" + reference)).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }

    @Test
    void anotherOrdersPaymentIsRefused() throws Exception {
        String mine = placeOrder();
        startPayPal(mine);
        String theirs = placeOrder();
        String theirPayment = startPayPal(theirs);

        mvc.perform(post("/api/orders/" + mine + "/paypal/capture").contentType(MediaType.APPLICATION_JSON).content(approved(theirPayment)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/orders/" + mine)).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }
}
