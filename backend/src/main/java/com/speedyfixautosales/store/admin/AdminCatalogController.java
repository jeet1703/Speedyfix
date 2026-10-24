package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.catalog.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Dashboard, products, fitment and plate checks. Every route needs a staff session (AdminWebConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminCatalogService catalog;

    public AdminCatalogController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    public record PlateRequest(@NotBlank(message = "Enter a registration number.") @Size(max = 10) String registration) {
    }

    @GetMapping("/dashboard")
    public AdminCatalogService.Dashboard dashboard() {
        return catalog.dashboard();
    }

    @GetMapping("/products")
    public List<AdminCatalogService.Row> products() {
        return catalog.list();
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return catalog.categories();
    }

    @GetMapping("/products/{id}")
    public AdminCatalogService.Detail product(@PathVariable String id) {
        return catalog.get(id);
    }

    /** multipart/form-data: "part" is the ProductForm as JSON, plus up to four "photos". */
    @PostMapping(path = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminCatalogService.Detail> create(@Valid @RequestPart("part") ProductForm form,
                                                             @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.create(form, photos));
    }

    /** POST rather than PUT, because servlet containers only reliably parse multipart bodies on POST. */
    @PostMapping(path = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminCatalogService.Detail update(@PathVariable String id, @Valid @RequestPart("part") ProductForm form,
                                             @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        return catalog.update(id, form, photos);
    }

    /** /api/admin/fitment?oe=39071660&oe=...&engineCode=&productId= */
    @GetMapping("/fitment")
    public List<AdminCatalogService.Fitment> fitment(@RequestParam(required = false) List<String> oe,
                                                     @RequestParam(required = false) String engineCode,
                                                     @RequestParam(required = false) String productId) {
        return catalog.suggestFitment(oe, engineCode, productId);
    }

    @PostMapping("/plate-check")
    public AdminCatalogService.PlateCheck plateCheck(@Valid @RequestBody PlateRequest request) {
        return catalog.checkPlate(request.registration());
    }
}
