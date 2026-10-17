package app.listful.auth;

public class BootstrapTokenException extends RuntimeException {
    public BootstrapTokenException() {
        super("A valid first-admin setup code is required");
    }
}
