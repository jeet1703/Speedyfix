package com.speedyfixautosales.store.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.speedyfixautosales.store.account.CustomerService;
import com.speedyfixautosales.store.account.VerificationMailer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

@SpringBootTest(properties = "account.email-sign-in=true")
@AutoConfigureMockMvc
class EmailSignInApiTest {

    /** Stands in for email: keeps the last code (or "already registered" note) sent to each address. */
    static final Map<String, String> SENT = new ConcurrentHashMap<>();

    @TestConfiguration
    static class FakeMail {
        @Bean
        @Primary
        VerificationMailer fakeMailer() {
            return new VerificationMailer() {
                @Override
                public void sendCode(String email, String name, String code) {
                    SENT.put(email, code);
                }

                @Override
                public void sendResetCode(String email, String name, String code) {
                    SENT.put(email, "reset:" + code);
                }

                @Override
                public void sendAlreadyRegistered(String email, CustomerService.SignInMethod method) {
                    SENT.put(email, "already:" + method);
                }

                @Override
                public boolean sendsEmail() {
                    return true;
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    private void signUp(String name, String email, String password) throws Exception {
        mvc.perform(post("/api/account/email/sign-up").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password)))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String email, String code) throws Exception {
        return mvc.perform(post("/api/account/email/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)));
    }

    @Test
    void signingUpEmailsACodeAndTheAccountIsMadeOnlyOnceItComesBack() throws Exception {
        mvc.perform(get("/api/site")).andExpect(jsonPath("$.emailSignIn").value(true));

        signUp("Dana Shopper", "dana@example.com", "long-enough-1");
        String code = SENT.get("dana@example.com");
        assertThat(code).matches("\\d{6}");

        // No account until the code comes back.
        mvc.perform(post("/api/account/email/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dana@example.com\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isUnauthorized());

        String wrong = code.equals("000000") ? "111111" : "000000";
        confirm("dana@example.com", wrong).andExpect(status().isBadRequest());

        String body = confirm("DANA@example.com", code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Dana Shopper"))
                .andExpect(jsonPath("$.customer.signInMethod").value("EMAIL"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(body, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dana@example.com"));

        // The code only works once.
        confirm("dana@example.com", code).andExpect(status().isBadRequest());

        mvc.perform(post("/api/account/email/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Dana@Example.com\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.customer.signIns").value(2));
        mvc.perform(post("/api/account/email/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dana@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("That email or password isn’t right."));
    }

    /** This test's requests come from their own address, so the other tests' requests don't use up its rate limit. */
    private org.springframework.test.web.servlet.ResultActions own(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json).with(r -> {
            r.setRemoteAddr("10.20.30.40");
            return r;
        }));
    }

    @Test
    void aForgottenPasswordIsResetWithAnEmailedCodeOnceADay() throws Exception {
        own("/api/account/email/sign-up", "{\"name\":\"Rae Shopper\",\"email\":\"rae@example.com\",\"password\":\"first-password-1\"}")
                .andExpect(status().isNoContent());
        own("/api/account/email/confirm", "{\"email\":\"rae@example.com\",\"code\":\"%s\"}".formatted(SENT.get("rae@example.com")))
                .andExpect(status().isOk());

        // Unknown addresses get the same answer, and no email.
        own("/api/account/email/forgot", "{\"email\":\"nobody@example.com\"}").andExpect(status().isNoContent());
        assertThat(SENT).doesNotContainKey("nobody@example.com");

        own("/api/account/email/forgot", "{\"email\":\"RAE@example.com\"}").andExpect(status().isNoContent());
        String code = SENT.get("rae@example.com");
        assertThat(code).startsWith("reset:");
        String reset = code.substring("reset:".length());
        String wrong = reset.equals("000000") ? "111111" : "000000";
        String body = "{\"email\":\"rae@example.com\",\"code\":\"%s\",\"password\":\"%s\"}";

        own("/api/account/email/reset", body.formatted(wrong, "second-password-2")).andExpect(status().isBadRequest());
        own("/api/account/email/reset", body.formatted(reset, "short")).andExpect(status().isBadRequest());
        own("/api/account/email/reset", body.formatted(reset, "second-password-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        // The old password no longer works, the new one does, and the code can't be used again.
        own("/api/account/email/sign-in", "{\"email\":\"rae@example.com\",\"password\":\"first-password-1\"}")
                .andExpect(status().isUnauthorized());
        own("/api/account/email/sign-in", "{\"email\":\"rae@example.com\",\"password\":\"second-password-2\"}")
                .andExpect(status().isOk());
        own("/api/account/email/reset", body.formatted(reset, "third-password-3")).andExpect(status().isBadRequest());

        // A second request the same day sends nothing.
        SENT.remove("rae@example.com");
        own("/api/account/email/forgot", "{\"email\":\"rae@example.com\"}").andExpect(status().isNoContent());
        assertThat(SENT).doesNotContainKey("rae@example.com");
    }

    @Test
    void signingUpAgainWithAnAddressThatHasAnAccountEmailsANoteInsteadOfACode() throws Exception {
        signUp("Eli", "eli@example.com", "long-enough-2");
        confirm("eli@example.com", SENT.get("eli@example.com")).andExpect(status().isOk());

        // Same answer as a new sign-up, so the form doesn't reveal who has an account.
        signUp("Someone else", "ELI@example.com", "another-password");
        assertThat(SENT.get("ELI@example.com")).isEqualTo("already:EMAIL");
    }

    @Test
    void shortPasswordsAndBadCodesAreTurnedAway() throws Exception {
        mvc.perform(post("/api/account/email/sign-up").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fay\",\"email\":\"fay@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        confirm("fay@example.com", "12ab56").andExpect(status().isBadRequest());
        // A code for a sign-up that never started.
        confirm("nobody@example.com", "123456").andExpect(status().isBadRequest());
    }
}
