package com.processing.config;

import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetryFactoryTest {

    private static final List<Long> BACKOFF_MS = List.of(1000L, 2000L, 4000L);

    @Test
    void shouldUseConfiguredAttemptsWhenCreatingRetry() {
        // Act
        Retry retry = RetryFactory.authorizationRetry(propertiesWithRetry(3, BACKOFF_MS));

        // Assert
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @ParameterizedTest(name = "attempt {0} -> {1} ms")
    @CsvSource({
            "0, 1000",
            "1, 1000",
            "2, 2000",
            "3, 4000",
            "5, 4000"
    })
    void shouldWaitByBackoffTableWhenAttemptIsRetried(int attempt, long expectedIntervalMs) {
        // Arrange
        Retry retry = RetryFactory.loggerRetry(propertiesWithRetry(3, BACKOFF_MS));

        // Act
        long intervalMs = retry.getRetryConfig().getIntervalFunction().apply(attempt);

        // Assert
        assertThat(intervalMs).isEqualTo(expectedIntervalMs);
    }

    @ParameterizedTest(name = "backoff={0}")
    @NullAndEmptySource
    void shouldWaitMinimalIntervalWhenBackoffIsNotConfigured(List<Long> backoffMs) {
        // Arrange
        Retry retry = RetryFactory.authorizationRetry(propertiesWithRetry(3, backoffMs));

        // Act
        long intervalMs = retry.getRetryConfig().getIntervalFunction().apply(1);

        // Assert
        assertThat(intervalMs).isEqualTo(1L);
    }

    private static SwitchProperties propertiesWithRetry(int maxAttempts, List<Long> backoffMs) {
        return new SwitchProperties("1.0.0", null, null, null, null, null,
                new SwitchProperties.RetryProperties(maxAttempts, backoffMs), null);
    }
}
