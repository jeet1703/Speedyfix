package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.chat.ChatService;
import com.speedyfixautosales.store.catalog.MenuGroup;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.order.OrderPaymentService;
import com.speedyfixautosales.store.order.PayPalClient;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The business details and Products menu that every page's header and footer need. */
@RestController
public class SiteController {

    private final SiteProperties site;
    private final CatalogRepository repository;
    private final String googleClientId;
    private final VehicleService vehicles;
    private final PayPalClient paypal;
    private final OrderPaymentService payments;
    private final ChatService chat;

    public SiteController(SiteProperties site, CatalogRepository repository,
                          @Value("${account.google-client-id:}") String googleClientId, VehicleService vehicles,
                          PayPalClient paypal, OrderPaymentService payments, ChatService chat) {
        this.site = site;
        this.repository = repository;
        this.googleClientId = googleClientId.trim();
        this.vehicles = vehicles;
        this.paypal = paypal;
        this.payments = payments;
        this.chat = chat;
    }

    /**
     * googleClientId is public (Google's button needs it in the browser); empty until Google sign-in is set up.
     * demoPlates is true until DVSA is connected, while only the sample plates resolve.
     * paypalClientId is public too (PayPal's buttons load with it); empty until PayPal is set up. unpaidHoldMinutes
     * is how long an unpaid order keeps its parts (0: no limit). chatAi is true when the help chat can answer with AI.
     */
    public record SiteResponse(HomeResponse.Site site, List<MenuGroup> menu, String googleClientId, boolean demoPlates,
                               String paypalClientId, long unpaidHoldMinutes, boolean chatAi) {
    }

    @GetMapping("/api/site")
    public SiteResponse site() {
        return new SiteResponse(HomeResponse.Site.from(site), repository.menu(), googleClientId, vehicles.demoPlates(),
                paypal.publicClientId(), payments.unpaidExpiry() == null ? 0 : payments.unpaidExpiry().toMinutes(), chat.enabled());
    }
}
