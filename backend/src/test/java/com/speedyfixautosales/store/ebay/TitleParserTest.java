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
    void bareEnginesAreKeptApartFromCompleteOnes() {
        assertThat(CategoryMapper.refine("engines", "Reconditioned Ford Ranger 2.0L YN2S Bare Engine Long Block 2018-2025")).isEqualTo("bare-engines");
        assertThat(CategoryMapper.refine("engines", "Renault Kadjar 1.2L DiG-T HRA2DDT H5F Short Motor Engine Block 2013-2018")).isEqualTo("short-motors");
        assertThat(CategoryMapper.refine("engines", "Ford Ranger Bare Cylinder Block 3.2L TDCI SAFA Diesel 2016-2021")).isEqualTo("cylinder-blocks");
        assertThat(CategoryMapper.refine("engines", "Used Jaguar F-Pace XE XF 2.0L Engine Bare Long Block 204DTD")).isEqualTo("bare-engines");
        assertThat(CategoryMapper.refine("engines", "Used Nissan Navara D40 2.5L YD25 DCi Complete Engine Assembly 2005-2006")).isEqualTo("engines");
        assertThat(CategoryMapper.refine("turbochargers", "Bare turbo core")).isEqualTo("turbochargers");
    }

    @Test
    void titlesThatSayWhatThePartIsOverrideTheEbayCategory() {
        assertThat(CategoryMapper.refine("steering-suspension", "Nissan Qashqai Automatic Engine Gearbox 1.2L DIG-T X421B Used Tested 2013-2017"))
                .isEqualTo("gearboxes");
        assertThat(CategoryMapper.refine("steering-suspension", "Jaguar XJ X351 3.0L SDV6 8 Speed Automatic Gearbox ZF Gen 2 2015-2019"))
                .isEqualTo("gearboxes");
        assertThat(CategoryMapper.refine("steering-suspension", "Land Rover Discovery Steering Rack 4 3.0L Diesel AH223K748AB")).isEqualTo("steering-suspension");
        assertThat(CategoryMapper.refine("drivetrain", "Volvo V40 V60 V70 S60 S80 Rear Gearbox Mount 2.0L Diesel 31430442")).isEqualTo("drivetrain");
        assertThat(CategoryMapper.refine("body-panels", "Ford Focus Front Right Complete Door Assembly Blue No Wing Mirror 2012-2018")).isEqualTo("doors");
        assertThat(CategoryMapper.refine("interior", "Jeep Grand Cherokee SRS Safety Bag Control Module 68222888AA 2011-2019")).isEqualTo("ecus-electronics");
        assertThat(CategoryMapper.refine("ecus-electronics", "Jaguar F-Pace Starter Motor X761 2.0L Diesel GX7311001AD")).isEqualTo("starters-alternators");
        assertThat(CategoryMapper.refine("fuel-injection", "Nissan Navara D40 Fuel Injector 3.0L Diesel 0445116033")).isEqualTo("fuel-injectors");
        assertThat(CategoryMapper.refine("fuel-injection", "Ford Ranger Fuel Injector Rail 3.2L")).isEqualTo("fuel-injection");
        assertThat(CategoryMapper.refine("egr-emissions", "Nissan Navara 2.3L D23 YS23 Diesel EGR Valve 147109869R")).isEqualTo("egr-valves");
        assertThat(CategoryMapper.refine("egr-emissions", "Jeep Cherokee Particular Filter & NOx Sensor WK2 3.0L CRD EXF DPF"))
                .isEqualTo("egr-emissions");
        assertThat(CategoryMapper.refine("engine-parts", "Suzuki Vitara 1.4L Petrol Variable Valve Timing VVT Solenoid")).isEqualTo("electrics-sensors");
        assertThat(CategoryMapper.refine("engine-parts", "Ford Ranger SAFA Diesel Engine Crankshaft 3.2L TDCI 2016-2021")).isEqualTo("crankshafts");
        assertThat(CategoryMapper.refine("engine-parts", "Mercedes-Benz A200 Diesel 2.1L CDi W176 Crankshaft Pulley")).isEqualTo("engine-parts");
        assertThat(CategoryMapper.refine("instruments-infotainment", "Jaguar XE CD Radio Player Head Unit 2.0L Diesel")).isEqualTo("audio-media");
        assertThat(CategoryMapper.refine("instruments-infotainment", "Nissan Qashqai Speedometer Instrument Cluster 4EA5A")).isEqualTo("instruments-infotainment");
    }

    @Test
    void ebayCategoriesMapToShopCategories() {
        assertThat(CategoryMapper.suggest("Engines")).isEqualTo("engines");
        assertThat(CategoryMapper.suggest("Engine Blocks")).isEqualTo("cylinder-blocks");
        assertThat(CategoryMapper.suggest("A/C Compressors & Clutches")).isEqualTo("ac-compressors");
        assertThat(CategoryMapper.suggest("Car Stereos & Head Units")).isEqualTo("audio-media");
        assertThat(CategoryMapper.suggest("Doors & Door Skins")).isEqualTo("doors");
        assertThat(CategoryMapper.suggest("Bonnet Panels")).isEqualTo("body-panels");
        assertThat(CategoryMapper.suggest("Throttle Bodies")).isEqualTo("air-intake");
        assertThat(CategoryMapper.suggest("Ignition Coils")).isEqualTo("ignition");
        assertThat(CategoryMapper.suggest("Instrument Clusters")).isEqualTo("instruments-infotainment");
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
