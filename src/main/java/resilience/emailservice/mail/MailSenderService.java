package resilience.emailservice.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import resilience.emailservice.exception.InvalidEmailException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; // Thread-safe List

@Service
public class MailSenderService {

    private static final Logger logger = LoggerFactory.getLogger(MailSenderService.class);

    @Autowired
    private SMTPClient smtpClient;

    // 성공적으로 전송된 이메일을 저장할 리스트 (Thread-safe)
    private final List<String> sentEmails = new CopyOnWriteArrayList<>();

    public void sendEmail(String email) {
        // 이메일 유효성 검증
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            // 유효하지 않으면 재시도 없이 바로 예외 발생
            throw new InvalidEmailException("유효하지 않은 이메일 주소입니다: " + email);
        }

        smtpClient.sendMail(email);
        logger.info("메일 전송 성공: {}", email);

        this.sentEmails.add(email);
        logger.info("성공 목록에 추가됨: {}, 현재 목록 크기: {}", email, sentEmails.size());
    }

    /**
     * 성공적으로 전송된 이메일 목록을 반환합니다.
     * @return 성공한 이메일 주소 목록 (수정 불가능한 리스트)
     */
    public List<String> getSentEmails() {
        return List.copyOf(this.sentEmails);
    }
}