package com.speedyfix.store.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Shop, product page, checkout and part requests. Orders change stock, so this class gets its own context. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckoutApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void shopFiltersByCategoryConditionAndPrice() throws Exception {
        mvc.perform(get("/api/shop").param("category", "engines").param("condition", "USED").param("maxPrice", "3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value("yd25-engine"))
                .andExpect(jsonPath("$.categories", hasSize(10)));
    }

    @Test
    void shopShowsOnlyPartsThatFitWhenAsked() throws Exception {
        mvc.perform(get("/api/shop").param("vehicleId", "navara").param("fitsOnly", "true"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].fit").value("FITS"));
    }

    @Test
    void shopPagesAndSortsByPrice() throws Exception {
        mvc.perform(get("/api/shop").param("sort", "price-high").param("pageSize", "4").param("page", "2"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.pageCount").value(4))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].id").value("rb25-turbo"));
    }

    @Test
    void unknownSortIsABadRequest() throws Exception {
        mvc.perform(get("/api/shop").param("sort", "cheapest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown sort cheapest."));
    }

    @Test
    void productPageHasFitmentRelatedPartsAndExVatPrice() throws Exception {
        mvc.perform(get("/api/products/yd25-engine").param("vehicleId", "navara"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.fit").value("FITS"))
                .andExpect(jsonPath("$.priceExVat").value(1579.17))
                .andExpect(jsonPath("$.fitsVehicles[0]").value("Nissan Navara D40, 2.5 dCi diesel"))
                .andExpect(jsonPath("$.related[0].categoryId").value("engines"));
    }

    @Test
    void placingAnOrderTakesStockAndEmptiesTheBasket() throws Exception {
        String basketId = id(mvc.perform(post("/api/baskets")).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/baskets/" + basketId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"gm-sensor\",\"quantity\":2}"));

        String body = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"basketId":"%s","name":"Test Shopper","email":"shopper@example.com","phone":"07700 900123",
                         "line1":"1 Example Street","town":"London","postcode":"sw1a1aa"}""".formatted(basketId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(matchesPattern("SF-[A-Z2-9]{8}")))
                .andExpect(jsonPath("$.address.postcode").value("SW1A 1AA"))
                .andExpect(jsonPath("$.total").value(45.95))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/orders/" + json.readTree(body).get("reference").asText())).andExpect(status().isOk());
        mvc.perform(get("/api/baskets/" + basketId)).andExpect(jsonPath("$.lines", hasSize(0)));
        mvc.perform(get("/api/products/gm-sensor")).andExpect(jsonPath("$.product.stock").value(10));
    }

    @Test
    void orderNeedsAValidPostcode() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"basketId":"x","name":"A","email":"a@example.com","phone":"07700 900123",
                         "line1":"1 Street","town":"London","postcode":"12345"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter a full UK postcode, like SW1A 1AA."));
    }

    @Test
    void partRequestKeepsOnlyAMaskedPlate() throws Exception {
        mvc.perform(multipart("/api/part-requests")
                        .file(new MockMultipartFile("photos", "old.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .param("registration", "ab12 cde").param("part", "Turbocharger")
                        .param("name", "Test Shopper").param("phone", "07700 900123").param("replyBy", "WHATSAPP"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(matchesPattern("PR-\\d+")))
                .andExpect(jsonPath("$.vehicle").value("Nissan Navara D40, 2.5 dCi diesel"))
                .andExpect(jsonPath("$.registration").doesNotExist());
    }

    @Test
    void partRequestByEmailNeedsAnEmail() throws Exception {
        mvc.perform(multipart("/api/part-requests").param("registration", "VX16 ABC").param("part", "Wiring loom")
                        .param("name", "Test Shopper").param("replyBy", "EMAIL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter your email address so we can reply by email."));
    }

    @Test
    void partRequestForAPlateNotInTheDataStillGoesThrough() throws Exception {
        mvc.perform(multipart("/api/part-requests").param("registration", "LM65 XYZ").param("part", "Gearbox")
                        .param("name", "Test Shopper").param("email", "shopper@example.com").param("replyBy", "EMAIL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle").value(nullValue()));
    }

    @Test
    void siteEndpointServesHeaderAndFooterData() throws Exception {
        mvc.perform(get("/api/site"))
                .andExpect(jsonPath("$.site.name").value("SpeedyFix Engine Solutions"))
                .andExpect(jsonPath("$.menu[0].links[0].href").value("/products?category=engines"));
    }

    private String id(String basketJson) throws Exception {
        JsonNode node = json.readTree(basketJson);
        return node.get("id").asText();
    }
}
