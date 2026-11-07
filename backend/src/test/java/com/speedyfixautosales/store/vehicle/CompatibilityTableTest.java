package com.speedyfixautosales.store.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompatibilityTableTest {

    /** As copied from the Compatibility table of eBay listing 366724568168 (first page, with the header). */
    private static final String FIRST_PAGE = """
            Make\tModel\tYear\tVariant\tType\tChassis\tEngine\tNotes
            Toyota\tLandCruiser\t2026\tDiesel Chassis Cab\t4.2 D 4x4\tAWD -- _J7_,_JL7_\t4164cc 100KW 136HP 1H-Z\t
            Toyota\tLandCruiser\t2026\tDiesel Off-Road Vehicle\t4.2 D (HZJ76, HZJ78, HZJ70,...\tAWD -- _J7_,_JL7_\t4164cc 96KW 131HP 1H-Z\t
            Toyota\tLandCruiser\t2025\tDiesel Chassis Cab\t4.2 D 4x4\tAWD -- _J7_,_JL7_\t4164cc 100KW 136HP 1H-Z\t
            """;

    /** A later page copied on its own: no header, so eBay's column order is assumed. */
    private static final String LATER_PAGE = """
            Toyota\tLandCruiser\t2024\tDiesel Chassis Cab\t4.2 D 4x4\tAWD -- _J7_,_JL7_\t4164cc 100KW 136HP 1H-Z\t
            Toyota\tLandCruiser\t2019\tDiesel Chassis Cab\t4.2 D 4x4\tAWD -- _J7_,_JL7_\t4164cc 100KW 136HP 1H-Z\t
            Page 2 of 7
            """;

    @Test
    void pastedRowsBecomeOneRangePerEngineAndRunOfYears() {
        List<VehicleRange> ranges = CompatibilityTable.ranges(CompatibilityTable.parse(FIRST_PAGE + LATER_PAGE));

        assertThat(ranges).extracting(VehicleRange::make, VehicleRange::model, VehicleRange::yearFrom, VehicleRange::yearTo,
                        VehicleRange::ebayEngine, VehicleRange::fuel)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Toyota", "LandCruiser", 2019, 2019, "4164cc 100KW 136HP 1H-Z", "diesel"),
                        org.assertj.core.groups.Tuple.tuple("Toyota", "LandCruiser", 2024, 2026, "4164cc 100KW 136HP 1H-Z", "diesel"),
                        org.assertj.core.groups.Tuple.tuple("Toyota", "LandCruiser", 2026, 2026, "4164cc 96KW 131HP 1H-Z", "diesel"));
        assertThat(ranges.get(0).engineCode()).isEqualTo("1H-Z");
    }

    @Test
    void textThatIsNotATableGivesNothing() {
        assertThat(CompatibilityTable.ranges(CompatibilityTable.parse("Toyota LandCruiser 2024 4164cc"))).isEmpty();
        assertThat(CompatibilityTable.ranges(CompatibilityTable.parse(null))).isEmpty();
    }
}
