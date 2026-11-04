package com.speedyfixautosales.store.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** One shopper can only hold so many parts in unpaid orders (here 3), so nobody can reserve the shop's stock unpaid. */
@SpringBootTest(properties = "orders.max-held-parts=3")
@AutoConfigureMockMvc
class HoldLimitApiTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions order(String email, int quantity) throws Exception {
        String basketId = JsonPath.read(mvc.perform(post("/api/baskets")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/baskets/" + basketId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"gm-sensor\",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"basketId":"%s","name":"Hold Tester","email":"%s","phone":"07700 900123",
                 "line1":"1 Example Street","town":"London","postcode":"SW1A 1AA"}""".formatted(basketId, email)));
    }

    @Test
    void unpaidOrdersCountTowardsTheLimitByEmail() throws Exception {
        order("holder@example.com", 2).andExpect(status().isCreated());
        // Another email address isn't affected.
        order("someone-else@example.com", 3).andExpect(status().isCreated());
        // Two held plus two more is over three, whatever the case of the email.
        order("Holder@Example.com", 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already have 2 parts reserved")));
        order("holder@example.com", 1).andExpect(status().isCreated());
    }

    @Test
    void oneOrderCantBeBiggerThanTheLimit() throws Exception {
        order("big@example.com", 4).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("up to 3 parts at a time")));
    }
}
