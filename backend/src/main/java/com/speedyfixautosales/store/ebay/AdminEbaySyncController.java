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

/**
 * The eBay sync: its status, and "Sync now". It also runs on its own every 30 minutes, and the admin no longer has a
 * button for it; these routes are for checking it by hand. Every route needs a staff session (AdminWebConfig).
 */
@RestController
@RequestMapping("/api/admin/ebay-sync")
@Profile("postgres")
public class AdminEbaySyncController {

    private final EbaySyncService sync;

    public AdminEbaySyncController(EbaySyncService sync) {
        this.sync = sync;
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
