package resilience.emailservice.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mail")
public class MailController {

    @Autowired
    private MailSenderService mailSenderService;

    // http://localhost:8081/mail/send 로 요청
    // POST 요청으로 email을 JSON 형식으로 받습니다.
    @PostMapping("/send")
    public String sendMail(@RequestBody EmailRequest emailRequest) {
        mailSenderService.sendEmail(emailRequest.getEmail());
        return "메일 전송 요청이 접수되었습니다.";
    }

    /**
     * 성공적으로 전송된 이메일 목록 전체를 조회합니다.
     * @return 성공한 이메일 주소 목록 (JSON 배열 형태)
     */
    @GetMapping("/list")
    public ResponseEntity<List<String>> getSentMailList() {
        List<String> sentEmails = mailSenderService.getSentEmails();
        return ResponseEntity.ok(sentEmails);
    }

}
