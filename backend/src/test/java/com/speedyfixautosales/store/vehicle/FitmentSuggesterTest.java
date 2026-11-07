package com.speedyfixautosales.store.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** How Gemini's suggestions are read and matched to the vehicle list; no Gemini or eBay calls. */
class FitmentSuggesterTest {

    private final FitmentSuggester suggester = new FitmentSuggester(null, "", List.of());

    @Test
    void readsGeminisListAndDropsImpossibleRows() throws Exception {
        List<FitmentSuggester.Suggestion> s = suggester.parse("""
                {"vehicles": [
                  {"make": "Land Rover", "model": "Range Rover Sport", "yearFrom": 2013, "yearTo": 2018, "engineCode": "306DT", "engineLitres": 3.0, "fuel": "diesel"},
                  {"make": "Jaguar", "model": "XF", "yearFrom": 2015, "yearTo": 2009},
                  {"make": "", "model": "XJ", "yearFrom": 2010, "yearTo": 2015}
                ]}""");
        assertThat(s).hasSize(1);
        assertThat(s.get(0).engineCode()).isEqualTo("306DT");
        assertThat(s.get(0).engineLitres()).isEqualTo(3.0);
        assertThat(suggester.parse("{\"vehicles\": []}")).isEmpty();
    }

    @Test
    void picksTheCatalogueModelGeminiMeant() {
        List<String> models = List.of("Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Sport");
        assertThat(FitmentSuggester.bestModel("Range Rover Sport", models)).contains("Range Rover Sport");
        assertThat(FitmentSuggester.bestModel("range rover sport L494", models)).contains("Range Rover Sport");
        assertThat(FitmentSuggester.bestModel("Discovery 4", models)).contains("Discovery");
        assertThat(FitmentSuggester.bestModel("Freelander", models)).isEmpty();
    }

    @Test
    void matchesEnginesByCodeOrBySizeAndFuel() {
        VehicleCatalogue.EngineChoice v6 = new VehicleCatalogue.EngineChoice("2993cc 155KW 211HP 306DT", "3.0L diesel, 211HP", "diesel", "306DT");
        VehicleCatalogue.EngineChoice petrol = new VehicleCatalogue.EngineChoice("2995cc 250KW 340HP 306PS", "3.0L petrol, 340HP", "petrol", "306PS");
        assertThat(FitmentSuggester.sameCode(v6, "306 DT")).isTrue();
        assertThat(FitmentSuggester.sameCode(petrol, "306DT")).isFalse();
        VehicleCatalogue.EngineChoice noCode = new VehicleCatalogue.EngineChoice("4999cc 375KW 510HP --", "5.0L petrol, 510HP", "petrol", "--");
        assertThat(FitmentSuggester.sameCode(noCode, "306DT")).isFalse();
        FitmentSuggester.Suggestion diesel = new FitmentSuggester.Suggestion("Land Rover", "Discovery", 2013, 2016, null, 3.0, "diesel");
        assertThat(FitmentSuggester.sameSizeAndFuel(v6, diesel)).isTrue();
        assertThat(FitmentSuggester.sameSizeAndFuel(petrol, diesel)).isFalse();
    }

    @Test
    void tellsGeminiOnlyTheDetailsGiven() {
        String text = FitmentSuggester.describe(new FitmentSuggester.Part("Range Rover Sport Injector", "9X2Q9K546DB", "", null, "Fuel Injectors", " "));
        assertThat(text).contains("Part number: 9X2Q9K546DB").contains("Category: Fuel Injectors").doesNotContain("Engine code").doesNotContain("Notes");
    }
}
