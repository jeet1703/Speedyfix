package com.speedyfix.store.web;

import com.speedyfix.store.partrequest.PartRequest;
import com.speedyfix.store.partrequest.PartRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/part-requests")
public class PartRequestController {

    private final PartRequestService requests;

    public PartRequestController(PartRequestService requests) {
        this.requests = requests;
    }

    public record PartRequestForm(
            @NotBlank(message = "Enter your registration number.") @Size(max = 10) String registration,
            @NotBlank(message = "Tell us which part you need.") @Size(max = 200) String part,
            @Size(max = 2000, message = "Keep the extra details under 2,000 characters.") String notes,
            @NotBlank(message = "Enter your name.") @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[+0-9 ()-]{10,20}$", message = "Enter a phone number, like 07700 900123.") String phone,
            @Email(message = "Enter an email address like name@example.com.") @Size(max = 200) String email,
            @NotNull(message = "Choose how we should reply.") PartRequest.ReplyBy replyBy) {
    }

    /** multipart/form-data: the form fields plus up to four "photos". */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PartRequestService.Received> submit(@Valid @ModelAttribute PartRequestForm form,
                                                              @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.submit(form.registration(), form.part(),
                form.notes(), form.name(), form.phone(), form.email(), form.replyBy(), photos));
    }
}
