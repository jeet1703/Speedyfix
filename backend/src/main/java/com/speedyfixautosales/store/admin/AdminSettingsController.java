package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final AdminSettings settings;
    private final VehicleService vehicles;

    public AdminSettingsController(AdminSettings settings, VehicleService vehicles) {
        this.settings = settings;
        this.vehicles = vehicles;
    }

    public record SettingsForm(
            @NotNull(message = "Enter the cost per lookup.") @DecimalMin(value = "0", message = "The cost can’t be below £0.")
            @DecimalMax(value = "10", message = "Enter a cost per lookup under £10.")
            @Digits(integer = 2, fraction = 4, message = "Enter the cost in pounds, like 0.05.") BigDecimal lookupCost,
            @NotNull(message = "Enter the monthly budget, or 0 for no cap.") @DecimalMin(value = "0", message = "The budget can’t be below £0.")
            @DecimalMax(value = "100000", message = "Enter a budget under £100,000.")
            @Digits(integer = 6, fraction = 2, message = "Enter the budget in pounds and pence.") BigDecimal lookupBudget,
            @Email(message = "Enter an email address like buying@example.com.") @Size(max = 200) String buyingTeamEmail) {
    }

    /** Settings plus read-only facts about this server, shown on the Settings page. */
    public record SettingsView(AdminSettings.Values values, String staffName, String staffEmail, long sessionHours, int demoVehicles) {
    }

    @GetMapping
    public SettingsView get(HttpServletRequest request) {
        return view(request);
    }

    @PutMapping
    public SettingsView update(@Valid @RequestBody SettingsForm form, HttpServletRequest request) {
        String email = form.buyingTeamEmail() == null ? "" : form.buyingTeamEmail().trim();
        settings.set(new AdminSettings.Values(form.lookupCost(), form.lookupBudget(), email));
        return view(request);
    }

    private SettingsView view(HttpServletRequest request) {
        AdminAuthService.Staff staff = AdminAuthInterceptor.staff(request);
        return new SettingsView(settings.get(), staff.name(), staff.email(), AdminAuthService.SESSION_LENGTH.toHours(), vehicles.all().size());
    }
}
