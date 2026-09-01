package app.listful.settings;

import app.listful.api.ApiError;
import app.listful.auth.AuthService;
import app.listful.auth.UsernameAlreadyExistsException;
import app.listful.domain.ListEntity;
import app.listful.domain.User;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.UserRepository;
import app.listful.settings.dto.AdminCreateUserRequest;
import app.listful.settings.dto.AdminListResponse;
import app.listful.settings.dto.AdminUpdateUserRequest;
import app.listful.settings.dto.AdminUserResponse;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public AdminUsersController(
        UserRepository userRepository,
        ListRepository listRepository,
        AuthService authService,
        MessageSource messageSource
    ) {
        this.userRepository = userRepository;
        this.listRepository = listRepository;
        this.authService = authService;
        this.messageSource = messageSource;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        User user = authService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PatchMapping("/users/{id}")
    public AdminUserResponse updateUser(@PathVariable String id, @Valid @RequestBody AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setActive(request.active());
        return toResponse(userRepository.save(user));
    }

    @GetMapping("/lists")
    @Transactional(readOnly = true)
    public List<AdminListResponse> listAllLists() {
        return listRepository.findAll().stream()
            .sorted(Comparator.comparing(ListEntity::getCreatedAt).reversed())
            .map(this::toListResponse)
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

    private AdminListResponse toListResponse(ListEntity list) {
        User owner = list.getUser();
        return new AdminListResponse(
            list.getId(),
            list.getTitle(),
            list.getDescription(),
            list.getType().name(),
            list.isPublicList(),
            owner.getId(),
            owner.getUsername(),
            owner.getEmail(),
            list.getTargetDate(),
            list.getCreatedAt()
        );
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<ApiError> usernameAlreadyExists(UsernameAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("username_taken", messageSource.getMessage("auth.username_taken", null, LocaleContextHolder.getLocale())));
    }
}
