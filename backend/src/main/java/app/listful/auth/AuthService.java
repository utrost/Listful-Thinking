package app.listful.auth;

import app.listful.auth.dto.AuthUserResponse;
import app.listful.auth.dto.LoginRequest;
import app.listful.auth.dto.RegisterRequest;
import app.listful.domain.User;
import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.UserRepository;
import app.listful.settings.SettingService;
import java.time.Instant;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final SettingService settingService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
        UserRepository userRepository,
        SettingService settingService,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.settingService = settingService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        boolean firstUser = userRepository.count() == 0;

        if (!firstUser && !settingService.registrationEnabled()) {
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
