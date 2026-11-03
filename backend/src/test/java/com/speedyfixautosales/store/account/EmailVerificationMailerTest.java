package com.speedyfixautosales.store.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.mail.EmailSender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailVerificationMailerTest {

    /** Resend, with or without a key, keeping what it was asked to send. */
    private static class FakeSender extends EmailSender {
        final List<Email> sent = new ArrayList<>();
        boolean down;

        FakeSender(boolean keys) {
            super(keys ? "re_test" : "", "SpeedyFix <orders@example.com>", "");
        }

        @Override
        public String send(Email email, String idempotencyKey) throws IOException {
            if (down) {
                throw new IOException("HTTP 503");
            }
            sent.add(email);
            return "id";
        }
    }

    private static EmailVerificationMailer mailer(FakeSender sender) {
        return new EmailVerificationMailer(sender, "SpeedyFix <account@example.com>", "https://shop.example/", "SpeedyFix Auto Sales");
    }

    @Test
    void sendsTheCodeFromTheAccountAddress() {
        FakeSender sender = new FakeSender(true);
        mailer(sender).sendCode("ann@example.com", "<b>Ann</b> Smith", "482913");
        assertThat(sender.sent).singleElement().satisfies(e -> {
            assertThat(e.from()).isEqualTo("SpeedyFix <account@example.com>");
            assertThat(e.subject()).startsWith("482913");
            assertThat(e.html()).contains("482913", "Hi &lt;b&gt;Ann&lt;/b&gt;,").doesNotContain("<b>Ann");
            assertThat(e.text()).contains("482913", "15 minutes");
        });
    }

    @Test
    void anAddressWithAnAccountIsToldHowToSignIn() {
        FakeSender sender = new FakeSender(true);
        mailer(sender).sendAlreadyRegistered("ann@example.com", CustomerService.SignInMethod.GOOGLE);
        assertThat(sender.sent).singleElement().satisfies(e ->
                assertThat(e.html()).contains("Sign in with Google", "https://shop.example/account"));
    }

    @Test
    void withoutEmailItOnlyLogsAndAFailedSendSaysTryAgain() {
        FakeSender none = new FakeSender(false);
        EmailVerificationMailer offline = mailer(none);
        offline.sendCode("ann@example.com", "Ann", "111111");
        assertThat(none.sent).isEmpty();
        assertThat(offline.sendsEmail()).isFalse();

        FakeSender down = new FakeSender(true);
        down.down = true;
        assertThatThrownBy(() -> mailer(down).sendCode("ann@example.com", "Ann", "111111"))
                .isInstanceOf(UnavailableException.class);
    }
}
