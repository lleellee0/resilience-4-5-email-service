package resilience.emailservice.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import resilience.emailservice.exception.SmtpConnectionException;
import java.util.Random;

@Component
public class SMTPClient {

    private static final Logger logger = LoggerFactory.getLogger(SMTPClient.class);

    private final Random random = new Random();

    public void sendMail(String email) {
        // 90% 확률로 예외 발생
        if(random.nextDouble() < 0.9) {
            logger.warn("SMTP 연결 실패: 서버에 접속할 수 없습니다.");
            throw new SmtpConnectionException("SMTP 연결 실패: 서버에 접속할 수 없습니다.");
        }

        // 정상 메일 전송 로직(예시)
        logger.info("{}로 메일 전송 성공", email);
    }
}
