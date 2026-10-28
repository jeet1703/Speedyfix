package com.speedyfixautosales.store.ebay;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the eBay import from the command line, then exits. See DATABASE.md, "Import the eBay listings":
 * <pre>mvn spring-boot:run -Dspring-boot.run.profiles=postgres -Dspring-boot.run.arguments="--import.ebay.csv=../data/imports/report.csv --spring.main.web-application-type=none"</pre>
 * Options: --import.ebay.dry-run=true, --import.ebay.limit=50, --import.ebay.details=false, --import.ebay.refresh-photos=true
 */
@Component
@Profile("postgres")
@ConditionalOnProperty("import.ebay.csv")
public class EbayImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EbayImportRunner.class);

    private final EbayImportService importer;
    private final ConfigurableApplicationContext context;
    private final String csv;
    private final EbayImportService.Options options;

    public EbayImportRunner(EbayImportService importer, ConfigurableApplicationContext context,
                            @Value("${import.ebay.csv}") String csv,
                            @Value("${import.ebay.dry-run:false}") boolean dryRun,
                            @Value("${import.ebay.limit:0}") int limit,
                            @Value("${import.ebay.details:true}") boolean details,
                            @Value("${import.ebay.refresh-photos:false}") boolean refreshPhotos) {
        this.importer = importer;
        this.context = context;
        this.csv = csv;
        this.options = new EbayImportService.Options(dryRun, limit, details, refreshPhotos);
    }

    @Override
    public void run(String... args) throws Exception {
        Path file = Path.of(csv).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            log.error("No CSV at {}", file);
            System.exit(SpringApplication.exit(context, () -> 1));
        }
        log.info("Importing {} ({})", file, options);
        EbayImportService.Summary s = importer.run(file, options);
        log.info("""

                eBay import {} finished{}
                  rows read        {}
                  created          {}
                  updated          {}
                  ended (qty 0)    {}
                  skipped          {}
                  part number from title {}
                  eBay details     {}
                  photos stored    {}
                  problems         {}""",
                s.runId(), options.dryRun() ? " (DRY RUN: nothing saved)" : "", s.rows(), s.created(), s.updated(), s.ended(),
                s.skipped(), s.withPartNumber(), s.detailsSkipped() ? "skipped" : s.detailsFound(), s.photos(), s.problems().size());
        s.problems().stream().limit(15).forEach(p -> log.info("  - {}", p));
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
