package com.speedyfixautosales.store.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.common.TooManyRequestsException;
import com.speedyfixautosales.store.common.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The admin sign-in lockout: 5 wrong tries from one address lock it for 15 minutes from the last one. */
class AdminAuthServiceTest {

    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "a-long-test-password";
    private static final String CLIENT = "203.0.113.9";

    private final AdminAuthService auth = new AdminAuthService(EMAIL, PASSWORD, "Owner", "");
    private Instant now = Instant.parse("2026-11-09T10:00:00Z");

    private void at(Duration later) {
        now = now.plus(later);
        auth.useClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void fiveWrongTriesLockSignInForFifteenMinutesFromTheLastOne() {
        at(Duration.ZERO);
        for (int left = 4; left >= 1; left--) {
            String expected = left + (left == 1 ? " try" : " tries") + " left";
            assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", CLIENT))
                    .isInstanceOf(UnauthorizedException.class).hasMessageContaining(expected);
            at(Duration.ofMinutes(2));
        }
        // The 5th wrong try locks sign-in.
        assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", CLIENT))
                .isInstanceOf(TooManyRequestsException.class).hasMessageContaining("locked for 15 minutes");

        // Even the right password is refused during the lock, which counts from the 5th try, not the 1st.
        at(Duration.ofMinutes(14));
        assertThatThrownBy(() -> auth.signIn(EMAIL, PASSWORD, CLIENT))
                .isInstanceOf(TooManyRequestsException.class).hasMessageContaining("Try again in 1 minute");

        at(Duration.ofMinutes(1));
        assertThat(auth.signIn(EMAIL, PASSWORD, CLIENT).token()).isNotBlank();
    }

    @Test
    void theLockIsPerAddressAndASuccessfulSignInClearsTheCount() {
        at(Duration.ZERO);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", CLIENT)).isInstanceOfAny(UnauthorizedException.class, TooManyRequestsException.class);
        }
        // Another address isn't locked out.
        assertThat(auth.signIn(EMAIL, PASSWORD, "198.51.100.4").token()).isNotBlank();

        // Four wrong tries, then the right one: the count starts again.
        String other = "192.0.2.50";
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", other)).isInstanceOf(UnauthorizedException.class);
        }
        assertThat(auth.signIn(EMAIL, PASSWORD, other).token()).isNotBlank();
        assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", other)).hasMessageContaining("4 tries left");
    }

    @Test
    void wrongTriesSpreadOverMoreThanFifteenMinutesAreForgotten() {
        at(Duration.ZERO);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", CLIENT)).isInstanceOf(UnauthorizedException.class);
        }
        at(Duration.ofMinutes(16));
        assertThatThrownBy(() -> auth.signIn(EMAIL, "wrong", CLIENT)).hasMessageContaining("4 tries left");
    }
}
