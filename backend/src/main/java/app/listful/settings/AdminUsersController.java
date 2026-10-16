package app.listful.settings;

import app.listful.api.ApiError;
import app.listful.api.ConflictException;
import app.listful.auth.AuthService;
import app.listful.auth.UsernameAlreadyExistsException;
import app.listful.lists.ListOwnershipService;
import app.listful.lists.dto.TransferOwnershipRequest;
import app.listful.domain.User;
import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.UserRepository;
import app.listful.lists.CurrentUser;
import app.listful.security.SecurityAuditService;
import app.listful.settings.dto.AdminCreateUserRequest;
import app.listful.settings.dto.AdminSetupUserRequest;
import app.listful.settings.dto.AdminSetupLinkResponse;
import app.listful.settings.dto.AdminListResponse;
import app.listful.settings.dto.AdminUpdateUserRequest;
import app.listful.settings.dto.AdminUserResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUsersController {
    private final UserRepository userRepository;
    private final ListRepository listRepository;
    private final AuthService authService;
    private final MessageSource messageSource;
    private final SecurityAuditService auditService;
    private final ListOwnershipService ownershipService;

    public AdminUsersController(
        UserRepository userRepository,
        ListRepository listRepository,
        AuthService authService,
        MessageSource messageSource,
        SecurityAuditService auditService,
        ListOwnershipService ownershipService
    ) {
        this.userRepository = userRepository;
        this.listRepository = listRepository;
        this.authService = authService;
        this.messageSource = messageSource;
        this.auditService = auditService;
        this.ownershipService = ownershipService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        User user = authService.createUser(request);
        auditService.record(
            "admin_user_created",
            CurrentUser.from(authentication).getId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getRequestURI(),
            "targetUserId=%s targetUsername=%s targetRole=%s".formatted(user.getId(), user.getUsername(), user.getRole().name())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PostMapping("/users/with-setup")
    @Transactional
    public ResponseEntity<AdminSetupLinkResponse> createUserWithSetup(@Valid @RequestBody AdminSetupUserRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        AuthService.SetupAccount created = authService.createUserWithSetup(request);
        auditService.recordRequired("admin_user_created_with_setup", CurrentUser.from(authentication).getId(),
            httpRequest.getRemoteAddr(), httpRequest.getRequestURI(),
            "targetUserId=%s targetUsername=%s targetRole=%s".formatted(created.user().getId(), created.user().getUsername(), created.user().getRole().name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AdminSetupLinkResponse(toResponse(created.user()),
            created.link().url(), created.link().expiresAt().toString(), created.link().emailed()));
    }

    @PostMapping("/users/{id}/setup-link")
    @Transactional
    public AdminSetupLinkResponse createSetupLink(@PathVariable String id, Authentication authentication, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AuthService.SetupLink link = authService.issueSetupLink(user);
        auditService.recordRequired("admin_setup_link_issued", CurrentUser.from(authentication).getId(),
            httpRequest.getRemoteAddr(), httpRequest.getRequestURI(), "targetUserId=%s targetUsername=%s".formatted(user.getId(), user.getUsername()));
        return new AdminSetupLinkResponse(toResponse(user), link.url(), link.expiresAt().toString(), link.emailed());
    }

    @PostMapping("/users/{id}/transfer-lists")
    public java.util.Map<String, Integer> transferInactiveUserLists(@PathVariable String id,
        @Valid @RequestBody TransferOwnershipRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        int count = ownershipService.transferInactiveUserLists(CurrentUser.from(authentication), id, request.username(),
            httpRequest.getRemoteAddr(), httpRequest.getRequestURI());
        return java.util.Map.of("transferred", count);
    }

    @PatchMapping("/users/{id}")
    @Transactional
    public AdminUserResponse updateUser(@PathVariable String id, @Valid @RequestBody AdminUpdateUserRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!request.active() && user.isActive() && user.getRole() == UserRole.ADMIN && userRepository.countByRoleAndActive(UserRole.ADMIN, 1) <= 1) {
            throw new ConflictException("last_active_admin", "At least one active admin account must remain.");
        }
        boolean oldActive = user.isActive();
        user.setActive(request.active());
        User saved = userRepository.save(user);
        if (oldActive != saved.isActive()) {
            auditService.record(
                "admin_user_active_changed",
                CurrentUser.from(authentication).getId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getRequestURI(),
                "targetUserId=%s targetUsername=%s active=%s".formatted(saved.getId(), saved.getUsername(), saved.isActive())
            );
        }
        return toResponse(saved);
    }

    @GetMapping("/lists")
    @Transactional(readOnly = true)
    public List<AdminListResponse> listAllLists() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream()
            .map(user -> new AdminListResponse(user.getId(), user.getUsername(), listRepository.countByUserId(user.getId())))
            .toList();
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name(),
            user.isActive(),
            user.getCreatedAt()
        );
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<ApiError> usernameAlreadyExists(UsernameAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("username_taken", messageSource.getMessage("auth.username_taken", null, LocaleContextHolder.getLocale())));
    }
}
