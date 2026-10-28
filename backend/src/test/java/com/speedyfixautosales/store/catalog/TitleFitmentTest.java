package com.speedyfixautosales.store.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.speedyfixautosales.store.vehicle.Vehicle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Titles from the business's eBay stock against vehicles as DVSA describes them. */
class TitleFitmentTest {

    private final TitleFitment fitment = new TitleFitment();

    private static Product part(String title) {
        return new Product(title.hashCode() + "", title, "engines", Condition.USED, null, BigDecimal.TEN, null, null, null, null,
                List.of(), null, List.of(), Shipping.PARCEL, 1, false, LocalDate.now(), ProductStatus.LIVE);
    }

    private static Vehicle car(String make, String model, String engine, Integer year) {
        return new Vehicle("v", null, make, model, engine, null, year);
    }

    @Test
    void sameMakeModelYearAndEngineSizeIsALikelyFit() {
        Product ecu = part("Nissan Qashqai J11 Auto Engine Control Unit 1.2L 237104ED9B 2014-2018");
        assertThat(fitment.judge(ecu, car("Nissan", "Qashqai", "1.2L petrol", 2016))).isEqualTo(TitleFitment.Verdict.LIKELY);
    }

    @Test
    void wrongYearEngineSizeModelOrMakeIsNotAFit() {
        Product ecu = part("Nissan Qashqai J11 Auto Engine Control Unit 1.2L 237104ED9B 2014-2018");
        assertThat(fitment.judge(ecu, car("Nissan", "Qashqai", "1.2L petrol", 2020))).isEqualTo(TitleFitment.Verdict.NO);
        assertThat(fitment.judge(ecu, car("Nissan", "Qashqai", "1.6L diesel", 2016))).isEqualTo(TitleFitment.Verdict.NO);
        assertThat(fitment.judge(ecu, car("Nissan", "Juke", "1.2L petrol", 2016))).isEqualTo(TitleFitment.Verdict.NO);
        assertThat(fitment.judge(ecu, car("Ford", "Focus", "1.0L petrol", 2016))).isEqualTo(TitleFitment.Verdict.NO);
    }

    @Test
    void makeSpellingsAndMultiWordModelsMatch() {
        assertThat(fitment.judge(part("Mercedes Benz A200D W177 Starter Motor 2.0L A2609060100 2018-2024"),
                car("Mercedes-Benz", "A200d", "2.0L diesel", 2019))).isEqualTo(TitleFitment.Verdict.LIKELY);
        assertThat(fitment.judge(part("Land Rover Range Rover Sport Fuel Injector 3.0L TDV6 9X2Q9K546DB 09-16"),
                car("Land Rover", "Range Rover Sport", "3.0L diesel", 2014))).isEqualTo(TitleFitment.Verdict.LIKELY);
        assertThat(fitment.judge(part("Mazda 3 6 CX-5 CX-7 CX-8 2.2L SH02 Used Complete Engine 2012-2019"),
                car("Mazda", "CX-5", "2.2L diesel", 2015))).isEqualTo(TitleFitment.Verdict.LIKELY);
    }

    @Test
    void titlesWithoutAMakeAreUnknown() {
        assertThat(fitment.judge(part("Aluminium oil catch can kit, universal"), car("Nissan", "Qashqai", "1.2L petrol", 2016)))
                .isEqualTo(TitleFitment.Verdict.UNKNOWN);
    }
}
