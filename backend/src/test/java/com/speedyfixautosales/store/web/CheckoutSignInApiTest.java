package com.speedyfixautosales.store.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.speedyfixautosales.store.account.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** With ORDERS_REQUIRE_SIGN_IN on (the live default), only signed-in shoppers can place an order. */
@SpringBootTest(properties = "orders.require-sign-in=true")
@AutoConfigureMockMvc
class CheckoutSignInApiTest {

    /** Stands in for Google: any credential signs in as that account. */
    @TestConfiguration
    static class FakeGoogle {
        @Bean
        @Primary
        GoogleTokenVerifier fakeGoogleVerifier() {
            return new GoogleTokenVerifier() {
                @Override
                public boolean enabled() {
                    return true;
                }

                @Override
                public GoogleIdentity verify(String credential) {
                    return new GoogleIdentity(credential, credential + "@example.com", "Shopper " + credential, null);
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    private MockHttpServletRequestBuilder order() throws Exception {
        String basketId = JsonPath.read(mvc.perform(post("/api/baskets")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/baskets/" + basketId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"gm-sensor\",\"quantity\":1}")).andExpect(status().isOk());
        return post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"basketId":"%s","name":"Erin Shopper","email":"erin@example.com","phone":"07700 900123",
                 "line1":"1 Example Street","town":"London","postcode":"sw1a1aa"}""".formatted(basketId));
    }

    @Test
    void guestsAreAskedToSignInAndSignedInShoppersCanOrder() throws Exception {
        mvc.perform(get("/api/site")).andExpect(jsonPath("$.signInToBuy").value(true));
        mvc.perform(order()).andExpect(status().isUnauthorized());
        mvc.perform(order().header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")).andExpect(status().isUnauthorized());

        String session = mvc.perform(post("/api/account/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"erin\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(order().header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(session, "$.token")))
                .andExpect(status().isCreated());
    }
}
