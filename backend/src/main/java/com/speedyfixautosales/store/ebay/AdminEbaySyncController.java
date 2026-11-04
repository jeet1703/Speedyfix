package com.speedyfixautosales.store.ebay;

import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.UnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The eBay sync for staff: its status, and "Sync now". Every route needs a staff session (AdminWebConfig). */
@RestController
@RequestMapping("/api/admin/ebay-sync")
@Profile("postgres")
public class AdminEbaySyncController {

    private final EbaySyncService sync;
    private final EbayCompatibilityImport compatibility;

    public AdminEbaySyncController(EbaySyncService sync, EbayCompatibilityImport compatibility) {
        this.sync = sync;
        this.compatibility = compatibility;
    }

    public record CompatibilityStatus(boolean available, EbayCompatibilityImport.Status status) {
    }

    @GetMapping("/compatibility")
    public CompatibilityStatus compatibility() {
        return new CompatibilityStatus(compatibility.available(), compatibility.status());
    }

    /** Starts copying every listing's eBay compatibility table onto its part (202); poll GET for progress. */
    @PostMapping("/compatibility")
    public ResponseEntity<CompatibilityStatus> importCompatibility() {
        if (!compatibility.available()) {
            throw new UnavailableException("Importing compatibility needs EBAY_DEV_ID and EBAY_USER_TOKEN (the seller’s eBay sign-in) as well as the eBay keys.");
        }
        if (!compatibility.start()) {
            throw new ConflictException("The compatibility import is already running.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(compatibility());
    }

    @GetMapping
    public EbaySyncService.Status status() {
        return sync.status();
    }

    /** Starts a sync in the background and answers straight away (202); poll GET for progress and the result. */
    @PostMapping
    public ResponseEntity<EbaySyncService.Status> now() {
        EbaySyncService.Status status = sync.status();
        if (!status.available()) {
            throw new UnavailableException("The eBay sync isn’t set up on this server. Add EBAY_CLIENT_ID and EBAY_CLIENT_SECRET.");
        }
        if (!sync.startNow()) {
            throw new ConflictException("A sync is already running. It usually takes under a minute, or a few with new listings.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(sync.status());
    }
}
