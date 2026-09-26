package app.listful.auth;

import app.listful.domain.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class ListfulUserPrincipal implements UserDetails {
    private final User user;
    private final String credentialHash;
    private final long sessionVersion;
    public long getSessionVersion() { return sessionVersion; }

    public ListfulUserPrincipal(User user) {
        this.user = user;
        this.credentialHash = user.getPasswordHash();
        this.sessionVersion = user.getSessionVersion();
    }

    public User user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return credentialHash;
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
