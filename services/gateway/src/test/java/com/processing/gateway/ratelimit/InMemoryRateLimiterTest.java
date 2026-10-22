package com.processing.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private static final String CLIENT = "10.0.0.1";
    private static final String OTHER_CLIENT = "10.0.0.2";

    private final FakeTicker ticker = new FakeTicker();

    @Test
    void shouldAllowRequestWhenBucketHasTokens() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);

        // Act
        boolean allowed = limiter.allowRequest(CLIENT);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectRequestWhenBucketIsEmpty() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);
        limiter.allowRequest(CLIENT);

        // Act
        boolean allowed = limiter.allowRequest(CLIENT);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldAllowRequestWhenOnlyAnotherClientBucketIsEmpty() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);
        limiter.allowRequest(CLIENT);

        // Act
        boolean allowed = limiter.allowRequest(OTHER_CLIENT);

        // Assert
        assertThat(allowed).isTrue();
    }

    @ParameterizedTest(name = "after {0} ms -> allowed={1}")
    @CsvSource({
            "999,  false",
            "1000, true",
            "5000, true"
    })
    void shouldAllowRequestOnlyWhenTokenIsRefilled(long elapsedMs, boolean expected) {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);
        limiter.allowRequest(CLIENT);
        ticker.advance(Duration.ofMillis(elapsedMs));

        // Act
        boolean allowed = limiter.allowRequest(CLIENT);

        // Assert
        assertThat(allowed).isEqualTo(expected);
    }

    @Test
    void shouldNotAccumulateTokensAboveCapacityWhenClientIsIdle() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);
        limiter.allowRequest(CLIENT);
        ticker.advance(Duration.ofSeconds(10));
        limiter.allowRequest(CLIENT);

        // Act
        boolean allowed = limiter.allowRequest(CLIENT);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldRejectAllRequestsWhenCapacityIsZero() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(0, 100);

        // Act
        boolean allowed = limiter.allowRequest(CLIENT);

        // Assert
        assertThat(allowed).isFalse();
    }

    private InMemoryRateLimiter limiter(int capacity, double refillPerSecond) {
        return InMemoryRateLimiter.forTesting(capacity, refillPerSecond, Duration.ofMinutes(10), 100, ticker);
    }

    private static final class FakeTicker implements Ticker {

        private long nanos = 1_000_000_000L;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
