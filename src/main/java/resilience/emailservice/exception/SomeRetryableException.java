package resilience.emailservice.exception;

public class SomeRetryableException extends RetryableException {

    public SomeRetryableException(String message) {
        super(message);
    }

}