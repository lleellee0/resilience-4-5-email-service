package resilience.emailservice.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import resilience.emailservice.exception.CustomUncheckedException;
import resilience.emailservice.exception.InvalidEmailException;
import resilience.emailservice.exception.SmtpConnectionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // SmtpConnectionException 처리
    @ExceptionHandler(SmtpConnectionException.class)
    public ResponseEntity<String> handleSmtpConnectionException(SmtpConnectionException ex) {
        logger.error("SMTP 연결 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("SMTP 연결 오류: " + ex.getMessage());
    }

    // CustomUncheckedException 처리
    @ExceptionHandler(CustomUncheckedException.class)
    public ResponseEntity<String> handleCustomUncheckedException(CustomUncheckedException ex) {
        logger.error("메일 전송 중 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("메일 전송 중 오류 발생: " + ex.getMessage());
    }

    // InvalidEmailException 처리
    @ExceptionHandler(InvalidEmailException.class)
    public ResponseEntity<String> handleInvalidEmailException(InvalidEmailException ex) {
        logger.error("유효하지 않은 이메일 주소 예외 발생", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("유효하지 않은 이메일 주소: " + ex.getMessage());
    }

    // 그 외 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception ex) {
        logger.error("예기치 못한 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("예기치 못한 오류가 발생하였습니다.");
    }
}