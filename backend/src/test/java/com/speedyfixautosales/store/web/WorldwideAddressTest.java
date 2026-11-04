package com.speedyfixautosales.store.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.order.Order;
import org.junit.jupiter.api.Test;

class WorldwideAddressTest {

    @Test
    void ukAddressesNeedAFullPostcodeAndPayVat() {
        Order.Address a = OrderController.address("1 High St", "", "Manchester", "m11ae", null);
        assertThat(a.postcode()).isEqualTo("M1 1AE");
        assertThat(a.country()).isEqualTo("GB");
        assertThat(a.export()).isFalse();
        assertThatThrownBy(() -> OrderController.address("1 High St", null, "Manchester", "M1", "GB"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void addressesAbroadAreExportsAndNeedAPostcodeOrZip() {
        Order.Address india = OrderController.address("12 MG Road", null, "New Delhi", " 110001 ", "in");
        assertThat(india.country()).isEqualTo("IN");
        assertThat(india.postcode()).isEqualTo("110001");
        assertThat(india.export()).isTrue();
        assertThatThrownBy(() -> OrderController.address("Office 4", null, "Dubai", "", "AE"))
                .isInstanceOf(BadRequestException.class);
        // The Isle of Man is in the UK VAT area.
        assertThat(OrderController.address("1 Main Rd", null, "Douglas", "IM1 1AA", "IM").export()).isFalse();
    }

    @Test
    void countriesWeDontDeliverToAreRefused() {
        assertThatThrownBy(() -> OrderController.address("1 Street", null, "Moscow", "101000", "RU")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> OrderController.address("1 Street", null, "Nowhere", "1", "ZZ")).isInstanceOf(BadRequestException.class);
    }
}
