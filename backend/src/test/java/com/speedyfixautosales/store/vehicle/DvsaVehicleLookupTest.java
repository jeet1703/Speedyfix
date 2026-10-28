package com.speedyfixautosales.store.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DvsaVehicleLookupTest {

    /** Stands in for DVSA: knows one plate, counts calls. */
    static class FakeDvsa extends DvsaClient {
        final AtomicInteger calls = new AtomicInteger();

        FakeDvsa() {
            super("id", "secret", "key", "https://example.invalid/token", "scope");
        }

        @Override
        public Optional<DvsaVehicle> find(String registration) throws IOException {
            calls.incrementAndGet();
            return "LB16ABC".equals(registration)
                    ? Optional.of(new DvsaVehicle("NISSAN", "QASHQAI", "Petrol", 1197, 2016, "Grey"))
                    : Optional.empty();
        }
    }

    @Test
    void realPlatesResolveThroughDvsaAndAreCached() {
        FakeDvsa dvsa = new FakeDvsa();
        VehicleService service = new VehicleService(new DvsaVehicleLookup(dvsa, new DemoVehicleLookup()));

        Vehicle v = service.lookUp("lb16 abc");
        assertThat(v.make()).isEqualTo("Nissan");
        assertThat(v.model()).isEqualTo("Qashqai");
        assertThat(v.engine()).isEqualTo("1.2L petrol");
        assertThat(v.year()).isEqualTo(2016);
        assertThat(v.registration()).isEqualTo("LB16ABC");

        service.lookUp("LB16ABC");
        assertThat(dvsa.calls).hasValue(1);

        // The id alone brings the vehicle back (a shopper's saved vehicle after a restart), without the plate.
        Vehicle again = service.findById(v.id()).orElseThrow();
        assertThat(again.model()).isEqualTo("Qashqai");
        assertThat(again.year()).isEqualTo(2016);
        assertThat(again.registration()).isNull();
        assertThat(v.id()).hasSizeLessThanOrEqualTo(80);
    }

    @Test
    void unknownPlatesAndDemoPlatesDontResolveButDemoVehiclesStayAvailableById() {
        VehicleService service = new VehicleService(new DvsaVehicleLookup(new FakeDvsa(), new DemoVehicleLookup()));
        assertThat(service.identify("AB12 CDE")).isEmpty();
        assertThat(service.findById("navara")).isPresent();
    }

    @Test
    void readsBothDvsaResponseShapes() throws Exception {
        ObjectMapper json = new ObjectMapper();
        DvsaClient.DvsaVehicle withMot = DvsaClient.parse(json.readTree("""
                {"registration":"LB16ABC","make":"LAND ROVER","model":"RANGE ROVER SPORT","firstUsedDate":"2016-03-01",
                 "fuelType":"Diesel","primaryColour":"Black","engineSize":"2993","motTests":[]}"""));
        assertThat(withMot.engineSizeCc()).isEqualTo(2993);
        assertThat(withMot.year()).isEqualTo(2016);
        assertThat(DvsaVehicleLookup.toVehicle(withMot).make()).isEqualTo("Land Rover");
        assertThat(DvsaVehicleLookup.toVehicle(withMot).engine()).isEqualTo("3.0L diesel");

        DvsaClient.DvsaVehicle brandNew = DvsaClient.parse(json.readTree("""
                {"registration":"LB25ABC","make":"BMW","model":"X5","manufactureYear":2025,"fuelType":"Hybrid Electric (Clean)",
                 "motTestDueDate":"2028-02-28"}"""));
        assertThat(brandNew.engineSizeCc()).isNull();
        assertThat(brandNew.year()).isEqualTo(2025);
        assertThat(DvsaVehicleLookup.toVehicle(brandNew).make()).isEqualTo("BMW");
    }
}
