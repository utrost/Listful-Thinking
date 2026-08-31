package app.listful.auth;

public class RegistrationDisabledException extends RuntimeException {
    public RegistrationDisabledException() {
        super("Registration is disabled");
    }
}
