package resilience.emailservice.mail.retry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        // LoggingRetryListener 등록
        retryTemplate.registerListener(new LoggingRetryListener());
        return retryTemplate;
    }
}