package app.listful.auth;

import app.listful.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveUserSessionFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;

    public ActiveUserSessionFilter(ObjectProvider<UserRepository> userRepositoryProvider) {
        this.userRepository = userRepositoryProvider.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (userRepository != null
                && authentication != null && authentication.getPrincipal() instanceof ListfulUserPrincipal principal
                && userRepository.findById(principal.user().getId()).filter(user -> user.isActive()).isEmpty()) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            if (!isPublicRequest(request)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/")
            || path.equals("/api/v1/health")
            || path.startsWith("/api/v1/share/")
            || (path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/me"));
    }
}
