package resilience.emailservice.mail.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff; // 또는 ExponentialBackOff 등 사용 가능
import resilience.emailservice.exception.InvalidEmailException;

@Configuration
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

    // DLQ 토픽 이름 접미사 (application.yml 등에서 설정 가능. 없다면 .DLT를 붙이도록 설정)
    @Value("${kafka.topic.dlt.suffix:.DLT}")
    private String dltSuffix;

    // 재시도 횟수 (application.yml 등에서 설정 가능, 0이면 재시도 없음)
    @Value("${kafka.listener.retry.attempts:3}") // 최초 1회 + 재시도 2회 = 총 3회 시도
    private int retryAttempts;

    // 재시도 간격 (ms) (application.yml 등에서 설정 가능)
    @Value("${kafka.listener.retry.interval:1000}") // 1초 간격
    private long retryInterval;

    /**
     * Kafka 리스너 컨테이너 팩토리 설정.
     * 위에서 정의한 DefaultErrorHandler를 사용하도록 설정합니다.
     * @param consumerFactory Spring Boot가 application.properties/yml 설정 기반으로 자동 구성해주는 ConsumerFactory
     * @param kafkaErrorHandler 위에서 @Bean으로 등록한 DefaultErrorHandler
     * @return 설정된 ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // *** 에러 핸들러 설정 ***
        factory.setCommonErrorHandler(kafkaErrorHandler);

        log.info("Configured ConcurrentKafkaListenerContainerFactory with custom DefaultErrorHandler.");
        return factory;
    }

    /**
     * 실패한 메시지를 DLQ 토픽으로 보내는 Recoverer 빈 설정
     * @param kafkaOperations KafkaTemplate 빈이 자동으로 주입됨 (메시지 발행 위해 필요)
     * @return DeadLetterPublishingRecoverer 인스턴스
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaOperations<Object, Object> kafkaOperations) {
        // 실패 시 동작 정의: 원본 토픽 이름 + 접미사(예: .DLT)를 가진 토픽으로 메시지 발행
        return new DeadLetterPublishingRecoverer(kafkaOperations,
                (consumerRecord, exception) -> {
                    log.warn("Retries exhausted for record [{}]. Sending to DLT.", consumerRecord, exception);
                    return new TopicPartition(consumerRecord.topic() + dltSuffix, -1); // -1: 파티션 자동 할당
                });
    }

    /**
     * Kafka 리스너를 위한 에러 핸들러 빈 설정
     * @param recoverer 위에서 정의한 DeadLetterPublishingRecoverer 빈이 주입됨
     * @return DefaultErrorHandler 인스턴스
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // 고정 간격으로 재시도하는 BackOff 전략 설정
        // FixedBackOff(interval, maxAttempts): maxAttempts는 재시도 횟수 (최초 시도 제외)
        FixedBackOff backOff = new FixedBackOff(retryInterval, retryAttempts - 1); // 최초 시도 1회를 제외한 재시도 횟수

        // DefaultErrorHandler 생성 시 recoverer와 backOff 전달
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 특정 예외는 재시도하지 않도록 설정 가능
        errorHandler.addNotRetryableExceptions(InvalidEmailException.class);

        log.info("Configured Kafka DefaultErrorHandler with {} total attempts and {}ms interval.", retryAttempts, retryInterval);
        return errorHandler;
    }

    /**
     * DLQ 토픽 자동 생성을 위한 NewTopic 빈 정의
     * @return NewTopic 빈
     */
    @Bean
    public NewTopic emailSendRequestsDLT() {
        String originalTopic = "email-send-requests";
        // DLQ 토픽은 보통 파티션 1개, 리플리카 1개로 만듦 (운영 환경에 따라 조정)
        return new NewTopic(originalTopic + dltSuffix, 1, (short) 1);
        // 또는 TopicBuilder 사용 가능
        // return TopicBuilder.name(originalTopic + dltSuffix).partitions(1).replicas(1).build();
    }

}