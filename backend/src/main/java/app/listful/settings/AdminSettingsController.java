package app.listful.settings;

import app.listful.lists.CurrentUser;
import app.listful.security.SecurityAuditService;
import app.listful.settings.dto.AdminSettingsResponse;
import app.listful.settings.dto.UpdateAdminSettingsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {
    private final SettingService settingService;
    private final SecurityAuditService auditService;

    public AdminSettingsController(SettingService settingService, SecurityAuditService auditService) {
        this.settingService = settingService;
        this.auditService = auditService;
    }

    @GetMapping
    public AdminSettingsResponse getSettings() {
        return new AdminSettingsResponse(settingService.registrationEnabled());
    }

    @PutMapping
    public AdminSettingsResponse updateSettings(@Valid @RequestBody UpdateAdminSettingsRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        boolean registrationEnabled = settingService.setRegistrationEnabled(request.registrationEnabled());
        auditService.record(
            "admin_registration_setting_changed",
            CurrentUser.from(authentication).getId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getRequestURI(),
            "registrationEnabled=%s".formatted(registrationEnabled)
        );
        return new AdminSettingsResponse(registrationEnabled);
    }
}
