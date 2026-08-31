package app.listful.auth;

import app.listful.auth.dto.AuthUserResponse;
import app.listful.auth.dto.LoginRequest;
import app.listful.auth.dto.RegisterRequest;
import app.listful.domain.User;
import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    static final String REGISTRATION_ENABLED_KEY = "registration_enabled";

    private final UserRepository userRepository;
    private final SettingRepository settingRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean defaultRegistrationEnabled;

    public AuthService(
        UserRepository userRepository,
        SettingRepository settingRepository,
        PasswordEncoder passwordEncoder,
        @Value("${listful.registration-enabled:false}") boolean defaultRegistrationEnabled
    ) {
        this.userRepository = userRepository;
        this.settingRepository = settingRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultRegistrationEnabled = defaultRegistrationEnabled;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        boolean firstUser = userRepository.count() == 0;

        if (!firstUser && !registrationEnabled()) {
            throw new RegistrationDisabledException();
        }

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }

        UserRole role = firstUser ? UserRole.ADMIN : UserRole.USER;
        return userRepository.save(new User(
            username,
            normalizeEmail(request.email()),
            passwordEncoder.encode(request.password()),
            role,
            Instant.now()
        ));
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return user;
    }

    public AuthUserResponse toResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }

    private boolean registrationEnabled() {
        return settingRepository.findById(REGISTRATION_ENABLED_KEY)
            .map(setting -> Boolean.parseBoolean(setting.getValue()))
            .orElse(defaultRegistrationEnabled);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
