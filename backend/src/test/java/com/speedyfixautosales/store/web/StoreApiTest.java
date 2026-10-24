package com.speedyfixautosales.store.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
                .andExpect(jsonPath("$.popularCategories[*].id", contains("engines", "gearboxes", "accessories", "turbochargers")))
                .andExpect(jsonPath("$.bestSelling", hasSize(4)))
                .andExpect(jsonPath("$.bestSelling[*].categoryId", everyItem(in(List.of("engines", "gearboxes")))))
                .andExpect(jsonPath("$.latest[*].categoryId", everyItem(in(List.of("engines", "gearboxes")))))
                .andExpect(jsonPath("$.latest[0].id").value("yd25-engine"))
                .andExpect(jsonPath("$.bestSelling[0].fit").value("NO_VEHICLE"));
    }

    @Test
    void homeMarksFitForTheSavedVehicle() throws Exception {
        mvc.perform(get("/api/home").param("vehicleId", "navara"))
                .andExpect(jsonPath("$.vehicle.model").value("Navara D40"))
                .andExpect(jsonPath("$.bestSelling[1].fit").value("FITS"))
                .andExpect(jsonPath("$.bestSelling[3].fit").value("DOES_NOT_FIT"));
    }

    @Test
    void plateLookupReturnsTheVehicleAndHowManyPartsFit() throws Exception {
        mvc.perform(post("/api/vehicles/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"ab12 cde\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.id").value("navara"))
                .andExpect(jsonPath("$.fittingParts").value(4));
    }

    @Test
    void shopFiltersByBrandSubTypeAndFeaturedRefinementWithCounts() throws Exception {
        mvc.perform(get("/api/shop").param("category", "engines"))
                .andExpect(jsonPath("$.brands[0].value").value("Toyota"))
                .andExpect(jsonPath("$.brands[0].count").value(2))
                .andExpect(jsonPath("$.subTypes[0].value").value("Complete engine"))
                .andExpect(jsonPath("$.refinements[*].value", hasItem("Petrol engine")));
        // Brand counts ignore the brand filter itself, so shoppers can still switch brand.
        mvc.perform(get("/api/shop").param("category", "engines").param("brand", "nissan"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("yd25-engine"))
                .andExpect(jsonPath("$.brands", hasSize(2)));
        mvc.perform(get("/api/shop").param("refinement", "Petrol engine").param("refinement", "Nissan Navara gearbox"))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void siteSaysWhetherGoogleSignInIsSetUp() throws Exception {
        mvc.perform(get("/api/site")).andExpect(jsonPath("$.googleClientId", endsWith(".apps.googleusercontent.com")));
    }

    @Test
    void blankPlateIsABadRequestWithAShopperFacingMessage() throws Exception {
        mvc.perform(post("/api/vehicles/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"registration\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter your registration number."));
    }
}
