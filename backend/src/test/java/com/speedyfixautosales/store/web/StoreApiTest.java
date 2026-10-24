package com.speedyfixautosales.store.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StoreApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void homeReturnsEveryHomepageSection() throws Exception {
        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slides", hasSize(4)))
                .andExpect(jsonPath("$.popularCategories", hasSize(4)))
                .andExpect(jsonPath("$.bestSelling", hasSize(4)))
                .andExpect(jsonPath("$.latest[0].id").value("xe-mount"))
                .andExpect(jsonPath("$.bestSelling[0].fit").value("NO_VEHICLE"));
    }

    @Test
    void homeMarksFitForTheSavedVehicle() throws Exception {
        mvc.perform(get("/api/home").param("vehicleId", "navara"))
                .andExpect(jsonPath("$.vehicle.model").value("Navara D40"))
                .andExpect(jsonPath("$.bestSelling[1].fit").value("FITS"))
                .andExpect(jsonPath("$.bestSelling[3].fit").value("UNCONFIRMED"));
    }

    @Test
    void plateLookupReturnsTheVehicleAndHowManyPartsFit() throws Exception {
        mvc.perform(post("/api/vehicles/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"ab12 cde\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.id").value("navara"))
                .andExpect(jsonPath("$.fittingParts").value(3));
    }

    @Test
    void blankPlateIsABadRequestWithAShopperFacingMessage() throws Exception {
        mvc.perform(post("/api/vehicles/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"registration\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter your registration number."));
    }
}
