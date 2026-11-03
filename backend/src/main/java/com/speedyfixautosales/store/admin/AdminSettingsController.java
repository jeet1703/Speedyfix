package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
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
            @Email(message = "Enter an email address like buying@example.com.") @Size(max = 200) String buyingTeamEmail) {
    }

    /** Settings plus read-only facts about this server, shown on the Settings page. */
    public record SettingsView(AdminSettings.Values values, String staffName, String staffEmail, long sessionHours, int demoVehicles,
                               boolean demoPlates) {
    }

    @GetMapping
    public SettingsView get(HttpServletRequest request) {
        return view(request);
    }

    @PutMapping
    public SettingsView update(@Valid @RequestBody SettingsForm form, HttpServletRequest request) {
        String email = form.buyingTeamEmail() == null ? "" : form.buyingTeamEmail().trim();
        settings.set(new AdminSettings.Values(email));
        return view(request);
    }

    private SettingsView view(HttpServletRequest request) {
        AdminAuthService.Staff staff = AdminAuthInterceptor.staff(request);
        return new SettingsView(settings.get(), staff.name(), staff.email(), AdminAuthService.SESSION_LENGTH.toHours(), vehicles.all().size(),
                vehicles.demoPlates());
    }
}
