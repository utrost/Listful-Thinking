package app.listful.settings;

import app.listful.domain.Setting;
import app.listful.domain.repository.SettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingService {
    public static final String REGISTRATION_ENABLED_KEY = "registration_enabled";

    private final SettingRepository settingRepository;
    private final boolean defaultRegistrationEnabled;

    public SettingService(
        SettingRepository settingRepository,
        @Value("${listful.registration-enabled:false}") boolean defaultRegistrationEnabled
    ) {
        this.settingRepository = settingRepository;
        this.defaultRegistrationEnabled = defaultRegistrationEnabled;
    }

    @Transactional(readOnly = true)
    public boolean registrationEnabled() {
        return settingRepository.findById(REGISTRATION_ENABLED_KEY)
            .map(setting -> Boolean.parseBoolean(setting.getValue()))
            .orElse(defaultRegistrationEnabled);
    }

    @Transactional
    public boolean setRegistrationEnabled(boolean enabled) {
        Setting setting = settingRepository.findById(REGISTRATION_ENABLED_KEY)
            .orElseGet(() -> new Setting(REGISTRATION_ENABLED_KEY, Boolean.toString(enabled)));
        setting.setValue(Boolean.toString(enabled));
        settingRepository.save(setting);
        return enabled;
    }
}
