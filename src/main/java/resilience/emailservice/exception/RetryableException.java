package resilience.emailservice.exception;

public class RetryableException extends CustomUncheckedException {

    public RetryableException(String message) {
        super(message);
    }

}