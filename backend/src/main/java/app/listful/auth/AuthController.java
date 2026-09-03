package app.listful.auth;

import app.listful.api.ApiError;
import app.listful.auth.dto.AuthUserResponse;
import app.listful.auth.dto.EmailLinkRequest;
import app.listful.auth.dto.LoginRequest;
import app.listful.auth.dto.PasswordResetConsumeRequest;
import app.listful.auth.dto.RegisterRequest;
import app.listful.auth.dto.TokenRequest;
import app.listful.config.SecurityHardeningFilter;
import app.listful.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final MessageSource messageSource;

    public AuthController(AuthService authService, MessageSource messageSource) {
        this.authService = authService;
        this.messageSource = messageSource;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        User user = authService.register(request);
        SessionAuthentication.authenticate(httpRequest, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.toResponse(user));
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        User user = authService.login(request);
        SessionAuthentication.authenticate(httpRequest, user);
        return authService.toResponse(user);
    }

    @PostMapping("/magic-link")
    public ResponseEntity<Void> requestMagicLink(@Valid @RequestBody EmailLinkRequest request) {
        authService.sendMagicLink(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/magic-link/consume")
    public AuthUserResponse consumeMagicLink(@Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        User user = authService.consumeMagicLink(request);
        SessionAuthentication.authenticate(httpRequest, user);
        return authService.toResponse(user);
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody EmailLinkRequest request) {
        authService.sendPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/consume")
    public ResponseEntity<Void> consumePasswordReset(@Valid @RequestBody PasswordResetConsumeRequest request) {
        authService.consumePasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        return Map.of("headerName", SecurityHardeningFilter.CSRF_HEADER, "token", SecurityHardeningFilter.ensureCsrfToken(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof ListfulUserPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.toResponse(principal.user()));
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    ResponseEntity<ApiError> registrationDisabled(RegistrationDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiError("registration_disabled", message("auth.registration_disabled")));
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<ApiError> usernameAlreadyExists(UsernameAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("username_taken", message("auth.username_taken")));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiError("bad_credentials", message("auth.bad_credentials")));
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }
}
