package app.listful.settings;

import app.listful.settings.dto.AdminSettingsResponse;
import app.listful.settings.dto.UpdateAdminSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {
    private final SettingService settingService;

    public AdminSettingsController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public AdminSettingsResponse getSettings() {
        return new AdminSettingsResponse(settingService.registrationEnabled());
    }

    @PutMapping
    public AdminSettingsResponse updateSettings(@Valid @RequestBody UpdateAdminSettingsRequest request) {
        return new AdminSettingsResponse(settingService.setRegistrationEnabled(request.registrationEnabled()));
    }
}
