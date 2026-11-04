package com.speedyfixautosales.store.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speedyfixautosales.store.basket.BasketService;
import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.basket.InMemoryBasketStore;
import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.catalog.InMemoryCatalogRepository;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.vehicle.DemoVehicleLookup;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

class DeliveryQuotesTest {

    /** FedEx's answer (shaped like the sandbox's), cut down: two services, one with a list rate too, and one in dollars. */
    private static final String FEDEX_REPLY = """
            {"output":{"rateReplyDetails":[
              {"serviceType":"FEDEX_PRIORITY","serviceName":"FedEx Priority®",
               "commit":{"dateDetail":{"dayOfWeek":"Wed","dayFormat":"2026-11-04T18:00:00"}},
               "ratedShipmentDetails":[
                 {"rateType":"LIST","currency":"GBP","totalNetCharge":30.00},
                 {"rateType":"ACCOUNT","currency":"GBP","totalNetCharge":18.00,"totalNetFedExCharge":15.00,"shipmentRateDetail":{"currency":"GBP","taxes":[{"type":"VAT","amount":3.00}]}}]},
              {"serviceType":"FEDEX_ECONOMY","serviceName":"Economy",
               "ratedShipmentDetails":[{"rateType":"ACCOUNT","totalNetCharge":10.20,"shipmentRateDetail":{"currency":"GBP","taxes":[{"type":"VAT","amount":1.70}]}}]},
              {"serviceType":"FEDEX_USD_ONLY","serviceName":"Other",
               "ratedShipmentDetails":[{"rateType":"ACCOUNT","currency":"USD","totalNetCharge":5.00}]}]}}""";

    private final CatalogService catalog = new CatalogService(new InMemoryCatalogRepository(), new VehicleService(new DemoVehicleLookup()));
    private final BasketService baskets = new BasketService(new InMemoryBasketStore(), catalog);
    private final ParcelSizes sizes = new ParcelSizes(new StaticListableBeanFactory().getBeanProvider(JdbcTemplate.class));

    /** FedEx with keys, answering with the reply above (or failing), and remembering what it was asked. */
    private static class FakeFedEx extends FedExClient {
        final List<List<Parcel>> asked = new ArrayList<>();
        final List<Destination> to = new ArrayList<>();
        final List<List<Commodity>> goods = new ArrayList<>();
        boolean down;

        FakeFedEx(boolean keys) {
            super(keys ? "key" : "", keys ? "secret" : "", keys ? "123456789" : "", "sandbox", "UB8 2JP", "GB", "12:00");
        }

        @Override
        public List<Rate> rates(Destination dest, List<Parcel> parcels, List<Commodity> commodities) throws IOException {
            asked.add(parcels);
            to.add(dest);
            goods.add(commodities);
            if (down) {
                throw new IOException("FedEx is down");
            }
            return parse(new ObjectMapper().readTree(FEDEX_REPLY));
        }
    }

    private DeliveryQuotes quotes(FedExClient fedex, String markupPercent, String handlingFee) {
        return new DeliveryQuotes(fedex, sizes, catalog, new BigDecimal(markupPercent), new BigDecimal(handlingFee), true);
    }

    private BasketView basket(String... productIds) {
        String id = baskets.create().id();
        for (String p : productIds) {
            baskets.addItem(id, p, 1);
        }
        return baskets.get(id);
    }

    @Test
    void readsTheAccountRateInPoundsWithoutVat() throws IOException {
        List<FedExClient.Rate> rates = FedExClient.parse(new ObjectMapper().readTree(FEDEX_REPLY));
        assertThat(rates).extracting(FedExClient.Rate::serviceType).containsExactly("FEDEX_ECONOMY", "FEDEX_PRIORITY");
        assertThat(rates.get(1).chargeExVat()).isEqualByComparingTo("15.00");
        assertThat(rates.get(1).arrives()).startsWith("2026-11-04");
    }

    @Test
    void pricesParcelsFromFedExWithMarkupAndVat() {
        DeliveryQuotes q = quotes(new FakeFedEx(true), "10", "1.00");
        List<DeliveryQuotes.Option> options = q.options(basket("gm-sensor"), "sw1a1aa");
        // Economy: (8.50 + 10% + £1) + VAT = 12.42; Priority: (15.00 + 10% + £1) + VAT = 21.00.
        assertThat(options).extracting(DeliveryQuotes.Option::id).containsExactly("FEDEX_ECONOMY", "FEDEX_PRIORITY", DeliveryQuotes.MANUAL);
        assertThat(options.get(0).price()).isEqualByComparingTo("12.42");
        assertThat(options.get(1).price()).isEqualByComparingTo("21.00");
        assertThat(options.get(1).label()).isEqualTo("FedEx Priority");
        assertThat(options.get(0).label()).isEqualTo("FedEx Economy");
        assertThat(options.get(1).arrives()).isEqualTo("2026-11-04");
    }

    @Test
    void theCheapestServiceIsFreeOverOneHundredPounds() {
        List<DeliveryQuotes.Option> options = quotes(new FakeFedEx(true), "0", "0").options(basket("gm-sensor", "astra-loom"), "SW1A 1AA");
        assertThat(options.get(0).price()).isEqualByComparingTo("0");
        assertThat(options.get(1).price()).isEqualByComparingTo("18.00");
    }

    @Test
    void sendsOneBoxPerPartWithTheStaffWeightWhenSet() {
        FakeFedEx fedex = new FakeFedEx(true);
        BasketView basket = basket("gm-sensor");
        sizes.setWeight("gm-sensor", new BigDecimal("0.4"));
        quotes(fedex, "0", "0").options(basket, "SW1A 1AA");
        assertThat(fedex.asked.get(0)).singleElement().satisfies(p -> assertThat(p.weightKg()).isEqualByComparingTo("0.4"));
    }

    @Test
    void enginesArePricedByFedExTooButNeverFree() {
        FakeFedEx fedex = new FakeFedEx(true);
        DeliveryQuotes q = quotes(fedex, "0", "0");
        BasketView engine = basket("yd25-engine");
        assertThat(engine.subtotal()).isGreaterThan(new BigDecimal("100"));
        List<DeliveryQuotes.Option> options = q.options(engine, "SW1A 1AA");
        assertThat(options).extracting(DeliveryQuotes.Option::id).containsExactly("FEDEX_ECONOMY", "FEDEX_PRIORITY", DeliveryQuotes.MANUAL);
        // Over £100, but heavy: the cheapest service isn't free.
        assertThat(options.get(0).price()).isEqualByComparingTo("10.20");
        assertThat(fedex.asked).hasSize(1);
        // Before the postcode, the basket says delivery is worked out at checkout rather than a pallet price.
        assertThat(q.beforeCheckout(engine).delivery().method()).isEqualTo(DeliveryQuotes.AT_CHECKOUT);
    }

    @Test
    void smallPartsWithAnEngineGoOnItsPallet() {
        FakeFedEx fedex = new FakeFedEx(true);
        DeliveryQuotes q = quotes(fedex, "0", "0");
        BasketView engineOnly = basket("yd25-engine");
        q.options(engineOnly, "SW1A 1AA");
        BigDecimal palletWeight = fedex.asked.get(0).get(0).weightKg();
        // FedEx won't quote freight and parcels together, so the sensor rides on the engine's pallet.
        q.options(basket("yd25-engine", "gm-sensor"), "SW1A 1AA");
        List<FedExClient.Parcel> sent = fedex.asked.get(1);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).weightKg()).isGreaterThan(palletWeight);
    }

    @Test
    void whenFedExCantPriceTheOrderOnlyManualDeliveryIsOffered() {
        FakeFedEx down = new FakeFedEx(true);
        down.down = true;
        DeliveryQuotes q = quotes(down, "0", "0");
        assertThat(q.options(basket("gm-sensor"), "SW1A 1AA")).extracting(DeliveryQuotes.Option::id).containsExactly(DeliveryQuotes.MANUAL);
        assertThat(q.options(basket("yd25-engine"), "SW1A 1AA")).extracting(DeliveryQuotes.Option::id).containsExactly(DeliveryQuotes.MANUAL);
        // A shopper who chose FedEx isn't quietly switched to manual delivery.
        assertThatThrownBy(() -> q.choose(basket("gm-sensor"), "SW1A 1AA", "FEDEX_PRIORITY")).isInstanceOf(ConflictException.class);
        assertThat(q.choose(basket("gm-sensor"), "SW1A 1AA", DeliveryQuotes.MANUAL).method()).isEqualTo(DeliveryQuotes.MANUAL);
        // Without FedEx set up at all, the flat prices still apply.
        assertThat(quotes(new FakeFedEx(false), "0", "0").options(basket("gm-sensor"), "SW1A 1AA"))
                .singleElement().satisfies(o -> assertThat(o.method()).isEqualTo("STANDARD"));
    }

    @Test
    void theOrderIsChargedTheChosenServiceAndTheBasketWaitsForThePostcode() {
        DeliveryQuotes q = quotes(new FakeFedEx(true), "0", "0");
        BasketView basket = basket("gm-sensor");
        assertThat(q.choose(basket, "SW1A 1AA", "FEDEX_PRIORITY").price()).isEqualByComparingTo("18.00");
        assertThat(q.choose(basket, "SW1A 1AA", "made-up").price()).isEqualByComparingTo("10.20");
        assertThat(q.beforeCheckout(basket).delivery().method()).isEqualTo(DeliveryQuotes.AT_CHECKOUT);
        assertThat(q.beforeCheckout(basket).total()).isEqualByComparingTo(basket.subtotal());
    }

    @Test
    void manualDeliveryIsOfferedForParcelsAndChargesNothingUpFront() {
        DeliveryQuotes q = quotes(new FakeFedEx(true), "0", "0");
        BasketView basket = basket("gm-sensor");
        assertThat(q.options(basket, "SW1A 1AA")).last().satisfies(o -> assertThat(o.id()).isEqualTo(DeliveryQuotes.MANUAL));
        BasketView.Delivery manual = q.choose(basket, "SW1A 1AA", DeliveryQuotes.MANUAL);
        assertThat(DeliveryQuotes.manual(manual)).isTrue();
        assertThat(manual.price()).isEqualByComparingTo("0");
    }

    @Test
    void abroadThePricesHaveNoUkVatNothingIsFreeAndCustomsGetTheGoods() {
        FakeFedEx fedex = new FakeFedEx(true);
        DeliveryQuotes q = quotes(fedex, "0", "0");
        BasketView basket = basket("gm-sensor", "astra-loom");
        assertThat(basket.subtotal()).isGreaterThan(new BigDecimal("100"));
        List<DeliveryQuotes.Option> options = q.options(basket, new FedExClient.Destination(" 110001 ", "New Delhi", "in"));
        // FedEx's charge as it is: no VAT added, and not free though the parts are over £100.
        assertThat(options.get(0).price()).isEqualByComparingTo("8.50");
        assertThat(options.get(1).price()).isEqualByComparingTo("15.00");
        assertThat(fedex.to.get(0)).isEqualTo(new FedExClient.Destination("110001", "New Delhi", "IN"));
        assertThat(fedex.goods.get(0)).hasSize(2).allSatisfy(c -> assertThat(c.description()).startsWith("Used car part"));
        // Customs value: the parts' price without VAT.
        BigDecimal value = fedex.goods.get(0).stream().map(FedExClient.Commodity::valueGbp).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(value).isEqualByComparingTo(BasketService.forExport(basket, basket.delivery()).subtotal());
    }

    @Test
    void anExportBasketDropsTheVatFromEveryPrice() {
        BasketView basket = basket("gm-sensor");
        BasketView export = BasketService.forExport(basket, new BasketView.Delivery("FEDEX", "FedEx International Economy", new BigDecimal("79.18")));
        BasketView.Line line = export.lines().get(0);
        assertThat(line.unitPrice()).isEqualByComparingTo(BasketService.exVat(basket.lines().get(0).unitPrice()));
        assertThat(export.vatIncluded()).isEqualByComparingTo("0");
        assertThat(export.total()).isEqualByComparingTo(export.subtotal().add(new BigDecimal("79.18")));
    }

    @Test
    void withoutFedExDeliveryAbroadIsQuotedByHand() {
        assertThat(quotes(new FakeFedEx(false), "0", "0").options(basket("gm-sensor"), new FedExClient.Destination("", "Dubai", "AE")))
                .extracting(DeliveryQuotes.Option::id).containsExactly(DeliveryQuotes.MANUAL);
    }
}
