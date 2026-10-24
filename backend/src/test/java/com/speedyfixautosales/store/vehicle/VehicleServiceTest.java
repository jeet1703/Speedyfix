package com.speedyfixautosales.store.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.NotFoundException;
import org.junit.jupiter.api.Test;

class VehicleServiceTest {

    private final VehicleService service = new VehicleService(new DemoVehicleLookup());

    @Test
    void findsDemoPlateWhateverTheSpacingAndCase() {
        assertThat(service.lookUp(" ab12 cde ").id()).isEqualTo("navara");
    }

    @Test
    void rejectsSomethingThatIsNotAUkRegistration() {
        assertThatThrownBy(() -> service.lookUp("HELLO-WORLD"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid UK registration");
    }

    @Test
    void explainsWhenAValidPlateIsNotInTheDemoData() {
        assertThatThrownBy(() -> service.lookUp("ZZ99ZZZ"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ZZ99 ZZZ");
    }
}
