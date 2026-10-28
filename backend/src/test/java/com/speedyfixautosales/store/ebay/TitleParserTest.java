package com.speedyfixautosales.store.ebay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Titles taken from the business's eBay listings report. */
class TitleParserTest {

    @Test
    void readsMakePartNumberEngineSizeAndYears() {
        TitleParser.Parsed p = TitleParser.parse("Nissan Qashqai J11 Auto Engine Control Unit 1.2L 237104ED9B 2014-2018");
        assertThat(p.make()).isEqualTo("Nissan");
        assertThat(p.partNumber()).isEqualTo("237104ED9B");
        assertThat(p.engineSize()).isEqualTo("1.2L");
        assertThat(p.yearFrom()).isEqualTo(2014);
        assertThat(p.yearTo()).isEqualTo(2018);
    }

    @Test
    void skipsModelCodesAndKeepsHyphenatedPartNumbers() {
        assertThat(TitleParser.parse("Mercedes Benz A200D W177  Starter Motor 2.0L A2609060100 Minor Damaged 2018-2024").partNumber())
                .isEqualTo("A2609060100");
        assertThat(TitleParser.parse("Hyundai Kia  Fuel Pressure Pump 1.6L T-GDI 35320-2B220 2012-2016").partNumber())
                .isEqualTo("35320-2B220");
        assertThat(TitleParser.parse("Mercedes Benz A200D W177  Starter Motor 2.0L A2609060100 Minor Damaged 2018-2024").make())
                .isEqualTo("Mercedes Benz");
    }

    @Test
    void readsTwoDigitYearsAndLongerMakes() {
        TitleParser.Parsed p = TitleParser.parse("Land Rover Range Rover Diesel Fuel Injector  3.0L TDV6 SDV6  9X2Q9K546DB 09-16");
        assertThat(p.make()).isEqualTo("Land Rover");
        assertThat(p.partNumber()).isEqualTo("9X2Q9K546DB");
        assertThat(p.yearFrom()).isEqualTo(2009);
        assertThat(p.yearTo()).isEqualTo(2016);
    }

    @Test
    void titlesWithoutAPartNumberGiveNone() {
        assertThat(TitleParser.parse("Ford Focus front bumper 2018-2021").partNumber()).isNull();
    }

    @Test
    void ebayCategoriesMapToShopCategories() {
        assertThat(CategoryMapper.suggest("Engines")).isEqualTo("engines");
        assertThat(CategoryMapper.suggest("Doors & Door Skins")).isEqualTo("body-panels");
        assertThat(CategoryMapper.suggest("ECUs & Computer Modules")).isEqualTo("ecus-electronics");
        assertThat(CategoryMapper.suggest("Gearboxes, Racks & Pinions")).isEqualTo("steering-suspension");
        assertThat(CategoryMapper.suggest("Something new from eBay")).isEqualTo("other-parts");
    }

    @Test
    void csvParserHandlesQuotesCommasAndTheBom() {
        assertThat(EbayCsv.parse("a,\"b, c\",\"say \"\"hi\"\"\"\r\n1,2,3"))
                .containsExactly(List.of("a", "b, c", "say \"hi\""), List.of("1", "2", "3"));
    }
}
