package com.speedyfixautosales.store.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** FedEx's arrival dates count from the day the parcel can really go out, not the moment the shopper asks. */
class FedExShipDateTest {

    private final FedExClient fedex = new FedExClient("", "", "", "sandbox", "UB8 2JP", "GB", "12:00");

    @Test
    void beforeTheCutOffItGoesToday() {
        assertThat(fedex.shipDate(ZonedDateTime.parse("2026-11-04T10:30:00Z[Europe/London]"))).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    void afterTheCutOffItGoesTheNextWeekday() {
        // 03:07 in India is 21:37 the evening before in London.
        assertThat(fedex.shipDate(ZonedDateTime.parse("2026-11-05T03:07:00+05:30[Asia/Kolkata]"))).isEqualTo(LocalDate.of(2026, 11, 5));
        // Friday evening goes on Monday.
        assertThat(fedex.shipDate(ZonedDateTime.parse("2026-11-06T15:00:00Z[Europe/London]"))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void atTheWeekendItGoesOnMonday() {
        assertThat(fedex.shipDate(ZonedDateTime.parse("2026-11-07T09:00:00Z[Europe/London]"))).isEqualTo(LocalDate.of(2026, 11, 9));
    }
}
