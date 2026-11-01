package com.speedyfixautosales.store.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Compatibility rows from eBay's vehicle catalogue, and which shoppers' cars they match. */
class VehicleRangeTest {

    private final VehicleRange qashqaiDiesel = VehicleRange.of("Nissan", "Qashqai", 2014, 2021, "1461cc 81KW 110HP K9K 636;K9K 646", "diesel");
    private final VehicleRange qashqaiAny = VehicleRange.of("Nissan", "Qashqai", 2021, 2014, null, null);

    /** A car as DVSA describes it, with its engine size and fuel carried in the id the way DvsaVehicleLookup does. */
    private static Vehicle dvsa(String make, String model, int year, int cc, String fuel) {
        return new Vehicle(DvsaVehicleLookup.encode(make, model, year, cc, fuel), null, make, model, "", null, year);
    }

    @Test
    void readsEbaysEngineName() {
        assertThat(qashqaiDiesel.cc()).isEqualTo(1461);
        assertThat(qashqaiDiesel.engineCode()).isEqualTo("K9K 636");
        assertThat(qashqaiDiesel.engineLabel()).isEqualTo("1.5L diesel, 110HP");
        assertThat(qashqaiAny.engineLabel()).isEqualTo("All engines");
        // Years given the wrong way round are put right, and the same row always gets the same id.
        assertThat(qashqaiAny.yearFrom()).isEqualTo(2014);
        assertThat(qashqaiAny.id()).isEqualTo(VehicleRange.of("nissan", "QASHQAI", 2014, 2021, "", null).id()).startsWith("cat-");
        assertThat(VehicleService.describe(qashqaiDiesel.toVehicle())).isEqualTo("Nissan Qashqai, 2014 to 2021, 1.5L diesel, 110HP (K9K 636)");
    }

    @Test
    void aCarFitsWhenItsMakeModelYearAndEngineAreInTheRow() {
        VehicleService vehicles = new VehicleService(new DemoVehicleLookup());
        String row = vehicles.addRange(qashqaiDiesel).id();
        List<String> part = List.of(row);

        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai", 2016, 1461, "diesel"))).isTrue();
        // DVSA's model often carries the trim; the size can differ by a few cc.
        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai Acenta Dci", 2019, 1490, "diesel"))).isTrue();
        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai", 2012, 1461, "diesel"))).as("before the range").isFalse();
        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai", 2016, 1197, "petrol"))).as("another engine").isFalse();
        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai", 2016, 1461, "petrol"))).as("another fuel").isFalse();
        assertThat(vehicles.fits(part, dvsa("Nissan", "Juke", 2016, 1461, "diesel"))).as("another model").isFalse();
        assertThat(vehicles.fits(part, dvsa("Renault", "Kadjar", 2016, 1461, "diesel"))).as("another make").isFalse();
    }

    @Test
    void aRowForAllEnginesFitsAnyEngineInItsYears() {
        VehicleService vehicles = new VehicleService(new DemoVehicleLookup());
        List<String> part = List.of(vehicles.addRange(qashqaiAny).id());
        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai", 2018, 1332, "petrol"))).isTrue();
        assertThat(vehicles.fits(part, dvsa("Nissan", "Qashqai", 2022, 1332, "petrol"))).isFalse();
        // Found again by id, for the product page and the admin.
        assertThat(vehicles.findById(part.get(0))).get().satisfies(v -> {
            assertThat(v.year()).isEqualTo(2014);
            assertThat(v.yearTo()).isEqualTo(2021);
        });
    }
}
