package resilience.emailservice.exception;

public class SmtpConnectionException extends RetryableException {

    public SmtpConnectionException(String message) {
        super(message);
    }

}