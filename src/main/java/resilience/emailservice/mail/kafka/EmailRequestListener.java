package resilience.emailservice.mail.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import resilience.emailservice.exception.InvalidEmailException;
import resilience.emailservice.exception.RetryableException;
import resilience.emailservice.mail.EmailRequest;
import resilience.emailservice.mail.MailSenderService;

@Service
public class EmailRequestListener {

    private static final Logger log = LoggerFactory.getLogger(EmailRequestListener.class);

    private final MailSenderService mailSenderService;

    private static final String ORIGINAL_TOPIC = "email-send-requests";
    private static final String DLT_SUFFIX = ".DLT"; // KafkaListenerConfig의 suffix와 동일하게
    private static final String DLT_TOPIC = ORIGINAL_TOPIC + DLT_SUFFIX;
    private static final String DLT_GROUP_ID = "${spring.kafka.consumer.group-id}" + DLT_SUFFIX; // 원본 그룹 ID + .DLT

    public EmailRequestListener(MailSenderService mailSenderService) {
        this.mailSenderService = mailSenderService;
    }

    @KafkaListener(
            topics = ORIGINAL_TOPIC, // 원본 토픽 구독
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory" // 에러 핸들러가 설정된 팩토리 사용
    )
    public void consumeEmailRequest(EmailRequest emailRequest) {
        log.info("Kafka 통해 EmailRequest 수신: To={}, Body={}", emailRequest.getEmail(), emailRequest.getEmailBody());

        try {
            log.info("MailSenderService 통해 이메일 전송 요청: {}", emailRequest.getEmail());
            mailSenderService.sendEmail(emailRequest.getEmail());
            log.info("이메일 요청 처리 성공: {}", emailRequest.getEmail());

        } catch (RetryableException e) {
            // 에러 핸들러가 재시도 후 DLT로 보내도록 예외를 다시 던집니다.
            log.warn("이메일 처리 중 재시도 가능 오류 발생 (대상: {}): {}. Kafka 에러 핸들러를 트리거합니다.",
                    emailRequest.getEmail(), e.getMessage());
            // 예외를 다시 바깥으로 던져 ErrorHandler가 인지하도록 함
            throw e;
        } catch (InvalidEmailException e) {
            // 재시도하지 않을 예외이기 때문에  ErrorHandler 설정에서 이 예외를 NonRetryable로 지정하고,
            // ConditionalRecoverer에서 이 예외 타입일 경우 DLT로 보내지 않고 로그만 남기도록 구현해야 함.
            // 리스너 코드 자체에서는 ErrorHandler를 트리거하기 위해 예외를 던져야 함.
            log.error("잘못된 이메일 데이터 감지 (대상: {}): {}. 폐기 가능성이 있어 Kafka 에러 핸들러를 트리거합니다.",
                    emailRequest.getEmail(), e.getMessage(), e);
            throw e; // ErrorHandler가 NonRetryable로 인지하고 ConditionalRecoverer를 호출하도록 던짐
        } catch (Exception e) {
            // 에러 핸들러가 재시도 후 DLT로 보내도록 예외를 다시 던집니다.
            log.error("이메일 처리 중 예상치 못한 오류 발생 (대상: {}): {}. Kafka 에러 핸들러를 트리거합니다.",
                    emailRequest.getEmail(), e.getMessage(), e);

            // 혹은 DLT 말고 아래처럼 알람을 받도록 처리할 수도 있습니다.
            // 예: 모니터링 시스템에 이벤트 전송, 슬랙/이메일 알림 등
            System.err.println("!!! 정의 안된 예외 발생: 개발팀 확인 필요 !!! " + e.getMessage());
            // --- 알림 로직 끝 ---

            // 정의 안된 예외이기 때문에 RuntimeException으로 감싸서 ErrorHandler가 처리하도록 던짐
            throw new RuntimeException("Unexpected error during email processing", e);
        }
    }

    // DLT 리스너: 실패한 메시지를 처리
    @KafkaListener(
            topics = DLT_TOPIC,         // DLT 토픽 구독
            groupId = DLT_GROUP_ID,     // DLT 처리를 위한 별도 그룹 ID
            containerFactory = "kafkaListenerContainerFactory" // 새로 정의해준 팩토리 사용
    )
    public void consumeDLTMessage(
            // 1. 메시지 본문 (Payload)
            EmailRequest failedRequest, // DLT 메시지 본문 (원본과 동일 타입 가정)

            // 2. DLT 메시지 자체의 기본 헤더 정보
            @Header(KafkaHeaders.RECEIVED_TOPIC) String dltTopic,         // 메시지를 수신한 토픽 (DLT 토픽 이름)
            @Header(KafkaHeaders.RECEIVED_PARTITION) int dltPartition,    // 메시지를 수신한 DLT 파티션 ID
            @Header(KafkaHeaders.OFFSET) long dltOffset,                 // DLT 토픽 내에서의 메시지 오프셋

            // 3. 원본 메시지 정보 (DeadLetterPublishingRecoverer가 추가)
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic, // 실패가 발생했던 원본 토픽 이름
            @Header(KafkaHeaders.DLT_ORIGINAL_PARTITION) int originalPartition, // 원본 파티션 ID
            @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) long originalOffset,     // 원본 오프셋

            // 4. 실패 원인 정보 (DeadLetterPublishingRecoverer가 추가)
            @Header(KafkaHeaders.DLT_EXCEPTION_FQCN) String exceptionFqcn,      // 실패 원인 예외의 전체 클래스 이름
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exceptionMessage, // 예외 메시지
            @Header(KafkaHeaders.DLT_EXCEPTION_STACKTRACE) String stacktrace    // 예외 스택트레이스 문자열
            // 필요하다면 다른 DLT 헤더 추가: DLT_ORIGINAL_TIMESTAMP, DLT_ORIGINAL_CONSUMER_GROUP 등
    ) {
        log.warn("===== DLT로부터 메시지 수신 =====");
        // 어떤 메시지가 DLT로 왔는지 식별 정보 로깅
        log.warn("DLT 위치: topic={}, partition={}, offset={}", dltTopic, dltPartition, dltOffset);
        log.warn("원본 위치: topic={}, partition={}, offset={}", originalTopic, originalPartition, originalOffset);
        // 실패한 데이터 로깅 (민감 정보는 마스킹 처리 필요할 수 있음)
        log.warn("실패한 요청 본문: {}", failedRequest);
        // 실패 원인 로깅
        log.warn("실패 예외: {}", exceptionFqcn);
        log.warn("실패 메시지: {}", exceptionMessage);
        // 스택 트레이스는 매우 길 수 있으므로 DEBUG 레벨로 로깅하거나 필요한 부분만 출력
        log.debug("실패 스택트레이스:\n{}", stacktrace);
        log.warn("-------------------------------------");


        // === DLT 메시지 처리 로직 ===
        log.info("원본 오프셋 {}의 DLT 메시지 처리 시작", originalOffset);
        // 만약 DLT 리스너 처리 중 또 예외가 발생하면 무한 루프에 빠질 수 있으므로 반드시 try-catch 처리
        try {
            // 예시: 실패 원인(exceptionFqcn)을 분석하여 처리 결정
            if (exceptionFqcn.contains("InvalidEmailException")) {
                // 데이터 문제: 수정 불가 시 영구 실패 처리 또는 알림
                log.error("[DLT-조치] 원본 오프셋 {}에서 잘못된 데이터 감지. 자동 재처리 불가. 알림을 발송합니다.", originalOffset);
                // sendAlert("Invalid data in DLT", failedRequest, exceptionMessage);
            } else if (exceptionFqcn.contains("RetryableException")) {
                // 일시적 문제였을 수 있음: 제한적으로 재시도 고려 또는 수동 처리 요청
                log.warn("[DLT-조치] 원본 오프셋 {}에서 재시도 가능 예외 감지. 수동 재시도 또는 확인이 필요합니다.", originalOffset);
                // (주의) 여기서 바로 재시도 로직(mailSenderService.sendEmail)을 넣으면,
                // 외부 시스템이 계속 불안정할 경우 DLT 리스너가 계속 실패하며 루프 돌 수 있음.
                // -> 재시도 횟수 제한, 지수 백오프 적용 또는 별도 재시도 큐 사용 필요
            } else {
                // 예상 못한 오류: 개발자 확인 필요
                log.error("[DLT-조치] 원본 오프셋 {}에서 예상치 못한 예외 타입 {} 발생. 확인이 필요합니다.", exceptionFqcn, originalOffset);
                // sendAlert("Unexpected error in DLT", failedRequest, exceptionMessage);
            }
            // 처리가 (성공적으로?) 완료되면 로그 남김
            log.info("원본 오프셋 {}의 DLT 메시지 처리 완료", originalOffset);

        } catch (Exception e) {
            // DLT 메시지 처리 로직 자체에서 오류 발생 시
            log.error("!!! 중요(CRITICAL): 원본 오프셋 {}의 DLT 메시지 처리 중 예외 발생: {}", originalOffset, e.getMessage(), e);
            // 이 경우 더 이상 자동 처리 어려움. 심각한 오류 알림 발송 등 필수 조치
            // sendCriticalAlert("DLT Processing failed!", e);
        }
        log.warn("=====================================");
    }
}