package com.speedyfixautosales.store.vehicle;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Uses DVSA for real plates when its credentials are set (DVSA_CLIENT_ID and the rest), otherwise the demo plates. */
@Configuration
public class VehicleLookupConfig {

    @Bean
    @Primary
    VehicleLookup vehicleLookup(DvsaClient dvsa, DemoVehicleLookup demo) {
        if (dvsa.enabled()) {
            LoggerFactory.getLogger(VehicleLookupConfig.class).info("Plate lookup: DVSA MOT History API");
            return new DvsaVehicleLookup(dvsa, demo);
        }
        LoggerFactory.getLogger(VehicleLookupConfig.class).info("Plate lookup: demo plates (DVSA credentials not set)");
        return demo;
    }
}
