package com.speedyfixautosales.store.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.catalog.InMemoryCatalogRepository;
import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.vehicle.DemoVehicleLookup;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BasketServiceTest {

    private final BasketService baskets = new BasketService(
            new CatalogService(new InMemoryCatalogRepository(), new VehicleService(new DemoVehicleLookup())));

    @Test
    void chargesPalletDeliveryForEngines() {
        String id = baskets.create().id();
        BasketView view = baskets.addItem(id, "yd25-engine", 1);
        assertThat(view.delivery().method()).isEqualTo("PALLET");
        assertThat(view.total()).isEqualByComparingTo("1974.00");
        assertThat(view.vatIncluded()).isEqualByComparingTo("329.00");
    }

    @Test
    void parcelDeliveryIsFreeFromOneHundredPounds() {
        String id = baskets.create().id();
        assertThat(baskets.addItem(id, "gm-sensor", 1).delivery().price()).isEqualByComparingTo("9.95");
        assertThat(baskets.addItem(id, "astra-loom", 1).delivery().price()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void refusesMoreThanTheStockAvailable() {
        String id = baskets.create().id();
        baskets.addItem(id, "rb25-turbo", 1);
        assertThatThrownBy(() -> baskets.addItem(id, "rb25-turbo", 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only 1 of this part is in stock.");
    }

    @Test
    void settingQuantityToZeroRemovesTheLine() {
        String id = baskets.create().id();
        baskets.addItem(id, "xe-mount", 2);
        BasketView view = baskets.setQuantity(id, "xe-mount", 0);
        assertThat(view.lines()).isEmpty();
        assertThat(view.delivery().method()).isEqualTo("NONE");
    }
}
