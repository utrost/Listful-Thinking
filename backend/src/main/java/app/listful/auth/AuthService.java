package app.listful.auth;

import app.listful.auth.dto.AuthUserResponse;
import app.listful.auth.dto.EmailLinkRequest;
import app.listful.auth.dto.LoginRequest;
import app.listful.auth.dto.PasswordResetConsumeRequest;
import app.listful.auth.dto.RegisterRequest;
import app.listful.auth.dto.TokenRequest;
import app.listful.domain.AuthToken;
import app.listful.domain.User;
import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.AuthTokenRepository;
import app.listful.domain.repository.UserRepository;
import app.listful.settings.dto.AdminCreateUserRequest;
import app.listful.settings.SettingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final SettingService settingService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    @Value("${spring.mail.host:}") private String mailHost;
    public boolean emailRecoveryAvailable() { return mailHost != null && !mailHost.isBlank(); }

    @Value("${listful.mail-from:}") private String mailFrom;

    public AuthService(
        UserRepository userRepository,
        AuthTokenRepository authTokenRepository,
        SettingService settingService,
        PasswordEncoder passwordEncoder,
        JavaMailSender mailSender,
        @Value("${listful.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.settingService = settingService;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
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
    public boolean registrationAvailable() {
        return userRepository.count() == 0 || settingService.registrationEnabled();
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return user;
    }

    @Transactional
    public User createUser(AdminCreateUserRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
        return userRepository.save(new User(
            username,
            normalizeEmail(request.email()),
            passwordEncoder.encode(request.password()),
            request.role(),
            Instant.now()
        ));
    }

    @Transactional
    public void sendMagicLink(EmailLinkRequest request) {
        sendTokenEmail(request, AuthToken.MAGIC_LINK, "Your Listful Thinking magic link", "/magic-login");
    }

    @Transactional
    public User consumeMagicLink(TokenRequest request) {
        AuthToken token = consumeToken(request.token(), AuthToken.MAGIC_LINK);
        User user = token.getUser();
        user.getRole();
        return user;
    }

    @Transactional
    public void sendPasswordReset(EmailLinkRequest request) {
        sendTokenEmail(request, AuthToken.PASSWORD_RESET, "Your Listful Thinking password reset", "/reset-password");
    }

    @Transactional
    public void consumePasswordReset(PasswordResetConsumeRequest request) {
        AuthToken token = consumeToken(request.token(), AuthToken.PASSWORD_RESET);
        token.getUser().setPasswordHash(passwordEncoder.encode(request.password()));
        authTokenRepository.deleteByUserId(token.getUser().getId());
    }

    public AuthUserResponse toResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }

    private void sendTokenEmail(EmailLinkRequest request, String purpose, String subject, String path) {
        if (!emailRecoveryAvailable()) throw new EmailDeliveryException("email_unavailable");
        String email = normalizeEmail(request.email());
        var matches = userRepository.findAllByEmail(email).stream()
            .filter(User::isActive)
            .filter(user -> request.username() == null || request.username().isBlank()
                || user.getUsername().equals(normalizeUsername(request.username())))
            .toList();
        if (matches.size() != 1) return;
        java.util.Optional.of(matches.get(0)).ifPresent(user -> {
            String rawToken = newToken();
            authTokenRepository.save(new AuthToken(user, tokenHash(rawToken), purpose, Instant.now().plus(Duration.ofMinutes(30)), Instant.now()));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            if (mailFrom != null && !mailFrom.isBlank()) message.setFrom(mailFrom);
            message.setSubject(subject);
            message.setText("Open this link to continue: " + publicBaseUrl + path + "?token=" + rawToken);
            try {
                mailSender.send(message);
            } catch (MailException ex) {
                logger.warn("Auth email delivery failed ({})", ex.getClass().getSimpleName());
                throw new EmailDeliveryException("email_delivery_failed");
            }
        });
    }

    private AuthToken consumeToken(String rawToken, String purpose) {
        AuthToken token = authTokenRepository.findByTokenHash(tokenHash(rawToken))
            .orElseThrow(() -> new BadCredentialsException("Invalid token"));
        Instant now = Instant.now();
        if (!token.usableFor(purpose, now)) {
            throw new BadCredentialsException("Invalid token");
        }
        if (!token.getUser().isActive()) {
            throw new BadCredentialsException("Invalid token");
        }
        token.markUsed(now);
        return token;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash auth token", ex);
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
