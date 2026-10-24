package com.speedyfixautosales.store.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.speedyfixautosales.store.account.GoogleTokenVerifier;
import com.speedyfixautosales.store.common.UnauthorizedException;
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

@SpringBootTest(properties = {"admin.email=owner@example.com", "admin.password=test-password-123"})
@AutoConfigureMockMvc
class AccountApiTest {

    /** Stands in for Google: "good-<id>" is a valid sign-in for that Google account, anything else is refused. */
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
                    if (!credential.startsWith("good-")) {
                        throw new UnauthorizedException("We couldn’t sign you in with Google. Try again.");
                    }
                    String id = credential.substring(5);
                    return new GoogleIdentity(id, id + "@example.com", "Shopper " + id, null);
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    private String signIn(String googleId) throws Exception {
        String body = mvc.perform(post("/api/account/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"good-" + googleId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value(googleId + "@example.com"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void invalidGoogleCredentialIsRefused() throws Exception {
        mvc.perform(post("/api/account/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"forged\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchHistoryIsKeptPerAccountNewestFirstWithoutDuplicates() throws Exception {
        String alice = signIn("alice");
        String bob = signIn("bob");
        for (String q : new String[] {"YD25 engine", "turbo", "yd25 ENGINE"}) {
            mvc.perform(post("/api/account/searches").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"" + q + "\"}").header(HttpHeaders.AUTHORIZATION, alice)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/account/searches").header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$", contains("yd25 ENGINE", "turbo")));
        mvc.perform(get("/api/account/searches").header(HttpHeaders.AUTHORIZATION, bob)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(delete("/api/account/searches").header(HttpHeaders.AUTHORIZATION, alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/account/searches").header(HttpHeaders.AUTHORIZATION, alice)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void staffSeeSignedInCustomersWithTheirSearches() throws Exception {
        String dave = signIn("dave");
        mvc.perform(post("/api/account/searches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"navara gearbox\"}").header(HttpHeaders.AUTHORIZATION, dave));
        String staff = "Bearer " + JsonPath.read(mvc.perform(post("/api/admin/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"test-password-123\"}"))
                .andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/customers").header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("dave@example.com"))
                .andExpect(jsonPath("$[0].signIns").value(1))
                .andExpect(jsonPath("$[0].recentSearches[0]").value("navara gearbox"));
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(jsonPath("$.customers.total").isNumber());
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        String carol = signIn("carol");
        mvc.perform(get("/api/account").header(HttpHeaders.AUTHORIZATION, carol))
                .andExpect(jsonPath("$.name").value("Shopper carol"));
        mvc.perform(delete("/api/account/session").header(HttpHeaders.AUTHORIZATION, carol)).andExpect(status().isNoContent());
        mvc.perform(get("/api/account").header(HttpHeaders.AUTHORIZATION, carol)).andExpect(status().isUnauthorized());
    }
}
