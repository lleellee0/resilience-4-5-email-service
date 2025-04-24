package resilience.emailservice.mail.retry;


import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("loggingRetryListener")
public class LoggingRetryListener implements RetryListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingRetryListener.class);

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        // 첫 시도 전에 호출됩니다. 특별한 처리가 필요 없으면 true를 반환합니다.
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 모든 재시도가 끝난 후 호출됩니다.
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 재시도할 때마다 호출됩니다.
        logger.info("재시도 {}회: {}", context.getRetryCount(), throwable.getMessage());
    }
}