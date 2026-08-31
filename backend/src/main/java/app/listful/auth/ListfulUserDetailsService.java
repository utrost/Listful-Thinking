package app.listful.auth;

import app.listful.domain.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ListfulUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public ListfulUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
            .map(ListfulUserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
