package com.speedyfixautosales.store.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"admin.email=owner@example.com", "admin.password=test-password-123", "admin.name=Catalogue staff"})
@AutoConfigureMockMvc
class AdminApiTest {

    /** Enough of a PNG for the signature check. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired
    private MockMvc mvc;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        String body = mvc.perform(post("/api/admin/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Owner@Example.com\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void adminRoutesNeedASession() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/products").header(HttpHeaders.AUTHORIZATION, "Bearer made-up"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session has ended. Sign in again."));
    }

    @Test
    void wrongEmailOrPasswordIsRefused() throws Exception {
        mvc.perform(post("/api/admin/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("That email or password isn’t right."));
        mvc.perform(post("/api/admin/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.com\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("That email or password isn’t right."));
    }

    @Test
    void tokenIsAJwtThatCantBeTamperedWith() throws Exception {
        String token = bearer.substring(7);
        String[] parts = token.split("\\.");
        org.junit.jupiter.api.Assertions.assertEquals(3, parts.length);
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionCanBeCheckedAndEnded() throws Exception {
        mvc.perform(get("/api/admin/session").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Catalogue staff"))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.role").value("MASTER"));
        mvc.perform(delete("/api/admin/session").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardCountsTheWorkQueue() throws Exception {
        // Seeded parts use sample photos, so every one is missing a real photo; seven have no confirmed vehicles.
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workQueue[0].workStatus").value("NEEDS_FITMENT"))
                .andExpect(jsonPath("$.lookupBudget").value(0));
    }

    @Test
    void draftCanBeSavedWithoutPriceOrPhotoButNotPublished() throws Exception {
        String draft = form("Opel Astra K engine room wiring loom", "DRAFT", null);
        String body = mvc.perform(multipart("/api/admin/products").file(json(draft)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", startsWith("opel-astra-k-engine-room-wiring-loom-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.workStatus").value("MISSING_PHOTO"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // Drafts stay out of the shop.
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());

        mvc.perform(multipart("/api/admin/products/" + id).file(json(form("Opel Astra K engine room wiring loom", "LIVE", null)))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("To publish, add a price and at least one photo. Or save it as a draft."));
    }

    @Test
    void partWithPriceAndPhotoCanBePublished() throws Exception {
        String live = form("Opel Astra K coolant sensor", "LIVE", "18.50");
        MockMultipartFile photo = new MockMultipartFile("photos", "sensor.png", "image/png", PNG);
        String body = mvc.perform(multipart("/api/admin/products").file(json(live)).file(photo).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.workStatus").value("LIVE"))
                .andExpect(jsonPath("$.vehicles[0].vehicleId").value("astra"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String photoUrl = JsonPath.read(body, "$.photos[0].url");

        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.product.imageUrl").value(photoUrl));
        mvc.perform(get(photoUrl.substring(photoUrl.indexOf("/api/"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        // Shoppers can find it by its cross-reference number.
        mvc.perform(get("/api/search").param("q", "9999-ABC")).andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    void photoThatIsNotAnImageIsRefused() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("photos", "x.png", "image/png", "<svg onload=alert(1)>".getBytes());
        mvc.perform(multipart("/api/admin/products").file(json(form("Bad photo part", "DRAFT", null))).file(fake)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Photos must be JPG, PNG or WebP images."));
    }

    @Test
    void fitmentIsSuggestedFromOeNumberAndEngineCode() throws Exception {
        mvc.perform(get("/api/admin/fitment").param("oe", "390-71660").param("engineCode", "yd25ddti")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value("astra"))
                .andExpect(jsonPath("$[0].source").value("OE match"))
                .andExpect(jsonPath("$[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$[1].vehicleId").value("navara"))
                .andExpect(jsonPath("$[1].confidence").value("MEDIUM"));
    }

    @Test
    void plateCheckListsConfirmedPartsAndIsNotLoggedAsAShopperSearch() throws Exception {
        mvc.perform(post("/api/admin/plate-check").contentType(MediaType.APPLICATION_JSON).content("{\"registration\":\"AB12 CDE\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.id").value("navara"))
                .andExpect(jsonPath("$.confirmed[*].id", hasItem("yd25-engine")))
                .andExpect(jsonPath("$.fitment.source").value("Donor plate"));
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.lookupsThisMonth").value(0));
    }

    @Test
    void partRequestStatusCanBeChanged() throws Exception {
        String body = mvc.perform(multipart("/api/part-requests")
                        .param("registration", "AB12 CDE").param("part", "Turbo").param("name", "Test shopper")
                        .param("phone", "07700 900123").param("replyBy", "PHONE"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(body, "$.reference");

        mvc.perform(get("/api/admin/part-requests").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$[0].reference").value(reference))
                .andExpect(jsonPath("$[0].maskedRegistration").value("AB12 ***"))
                .andExpect(jsonPath("$[0].status").value("NEW"));
        mvc.perform(patch("/api/admin/part-requests/" + reference).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUOTED\"}").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"));
    }

    @Test
    void settingsAreValidatedAndSaved() throws Exception {
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookupCost\":-1,\"lookupBudget\":50}").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The cost can’t be below £0."));
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookupCost\":0.05,\"lookupBudget\":50,\"buyingTeamEmail\":\"buying@example.com\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.lookupBudget").value(50))
                .andExpect(jsonPath("$.values.buyingTeamEmail").value("buying@example.com"));
    }

    private static MockMultipartFile json(String form) {
        return new MockMultipartFile("part", "", MediaType.APPLICATION_JSON_VALUE, form.getBytes());
    }

    private static String form(String name, String status, String price) {
        return """
                {"name":"%s","categoryId":"electrics-sensors","condition":"USED","grade":"A","price":%s,
                 "oeNumber":"39071660","crossReferences":["9999-ABC"],"engineCode":"","shipping":"PARCEL","stock":1,
                 "bestSeller":false,"vehicleIds":["astra"],"keepPhotoIds":[],"status":"%s"}
                """.formatted(name, price == null ? "null" : price, status);
    }
}
