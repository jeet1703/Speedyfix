package com.speedyfixautosales.store.web;

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

/** An order to India: an export, so no UK VAT; without FedEx set up, delivery abroad is quoted by hand. */
@SpringBootTest
@AutoConfigureMockMvc
class WorldwideOrderApiTest {

    @Autowired
    private MockMvc mvc;

    private String basketWithSensor() throws Exception {
        String basketId = JsonPath.read(mvc.perform(post("/api/baskets")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/baskets/" + basketId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"gm-sensor\",\"quantity\":1}")).andExpect(status().isOk());
        return basketId;
    }

    @Test
    void anOrderToIndiaHasNoUkVatAndKeepsItsCountry() throws Exception {
        String basketId = basketWithSensor();
        mvc.perform(post("/api/baskets/" + basketId + "/delivery-options").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postcode\":\"110001\",\"town\":\"New Delhi\",\"country\":\"IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.export").value(true))
                .andExpect(jsonPath("$.basket.vatIncluded").value(0.0))
                .andExpect(jsonPath("$.options[0].id").value("MANUAL"));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"basketId":"%s","name":"Ravi Kumar","email":"ravi@example.com","phone":"+91 98765 43210",
                         "line1":"12 MG Road","town":"New Delhi","postcode":"110001","country":"in"}""".formatted(basketId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.country").value("IN"))
                .andExpect(jsonPath("$.address.postcode").value("110001"))
                .andExpect(jsonPath("$.vatIncluded").value(0.0))
                .andExpect(jsonPath("$.delivery.method").value("MANUAL"));
    }

    @Test
    void aUkOrderStillNeedsAFullPostcode() throws Exception {
        String basketId = basketWithSensor();
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"basketId":"%s","name":"Erin Shopper","email":"erin@example.com","phone":"07700 900123",
                         "line1":"1 Example Street","town":"London","postcode":"SW1A"}""".formatted(basketId)))
                .andExpect(status().isBadRequest());
    }
}
