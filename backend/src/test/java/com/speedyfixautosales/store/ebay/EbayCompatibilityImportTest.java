package com.speedyfixautosales.store.ebay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.vehicle.VehicleRange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reading a listing's compatibility table from eBay's GetItem, and turning its rows into the shop's vehicle rows. */
class EbayCompatibilityImportTest {

    private static String row(String make, String model, String year, String engine, String variant) {
        return """
                <Compatibility>
                  <NameValueList><Name>Car Make</Name><Value>%s</Value></NameValueList>
                  <NameValueList><Name>Model</Name><Value>%s</Value></NameValueList>
                  <NameValueList><Name>Cars Year</Name><Value>%s</Value></NameValueList>
                  <NameValueList><Name>Engine</Name><Value>%s</Value></NameValueList>
                  <NameValueList><Name>Variant</Name><Value>%s</Value></NameValueList>
                  <CompatibilityNotes></CompatibilityNotes>
                </Compatibility>""".formatted(make, model, year, engine, variant);
    }

    @Test
    void readsTheTableAndGroupsConsecutiveYears() throws IOException {
        String engine = "2993cc 170KW 231HP V9X";
        String reply = """
                <?xml version="1.0" encoding="UTF-8"?>
                <GetItemResponse xmlns="urn:ebay:apis:eBLBaseComponents"><Ack>Success</Ack><Item><ItemCompatibilityList>
                %s%s%s%s%s
                </ItemCompatibilityList></Item></GetItemResponse>""".formatted(
                row("Nissan", "Navara", "2010", engine, "Diesel Pickup"),
                row("Nissan", "Navara", "2011", engine, "Diesel Pickup"),
                row("Nissan", "Navara", "2012", engine, "Diesel Pickup"),
                row("Nissan", "Navara", "2015", engine, "Diesel Pickup"),
                row("Nissan", "Pathfinder", "2011", engine, "Diesel SUV"));
        List<Map<String, String>> table = EbayTradingClient.parse(reply.getBytes(StandardCharsets.UTF_8));
        assertThat(table).hasSize(5);
        assertThat(table.get(0)).containsEntry("Car Make", "Nissan").containsEntry("Cars Year", "2010");

        List<VehicleRange> ranges = EbayCompatibilityImport.ranges(table);
        assertThat(ranges).extracting(r -> r.model() + " " + r.yearFrom() + "-" + r.yearTo())
                .containsExactly("Navara 2010-2012", "Navara 2015-2015", "Pathfinder 2011-2011");
        assertThat(ranges.get(0).ebayEngine()).isEqualTo(engine);
        assertThat(ranges.get(0).fuel()).isEqualTo("diesel");
    }

    @Test
    void anEbayErrorSaysWhatWentWrong() {
        String reply = """
                <GetItemResponse xmlns="urn:ebay:apis:eBLBaseComponents"><Ack>Failure</Ack>
                <Errors><ErrorCode>931</ErrorCode><LongMessage>Auth token is invalid.</LongMessage></Errors></GetItemResponse>""";
        assertThatThrownBy(() -> EbayTradingClient.parse(reply.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("931").hasMessageContaining("Auth token is invalid");
    }

    @Test
    void aListingWithoutATableHasNoRows() throws IOException {
        String reply = "<GetItemResponse xmlns=\"urn:ebay:apis:eBLBaseComponents\"><Ack>Success</Ack><Item/></GetItemResponse>";
        assertThat(EbayTradingClient.parse(reply.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }
}
