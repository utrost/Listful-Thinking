package app.listful.auth;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String code) { super(code); }
}
