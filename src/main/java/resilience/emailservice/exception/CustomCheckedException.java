package resilience.emailservice.exception;

public class CustomCheckedException extends Exception {
    public CustomCheckedException(String message) {
        super(message);
    }
}
