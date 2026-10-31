package com.processing.gateway.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCircuitBreakerTest {

    private static final String SERVICE = "switch";
    private static final Duration OPEN_DURATION = Duration.ofSeconds(10);
    private static final int THRESHOLD = 3;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-21T10:15:30Z"));

    private InMemoryCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = InMemoryCircuitBreaker.forTesting(OPEN_DURATION, THRESHOLD, clock);
    }

    @Test
    void shouldAllowRequestWhenNoFailuresHappened() {
        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldStayClosedWhenFailuresAreBelowThreshold() {
        // Arrange
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldOpenWhenFailuresReachThreshold() {
        // Arrange
        openCircuit();

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldResetFailureCountWhenSuccessIsRecorded() {
        // Arrange
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);
        breaker.recordSuccess(SERVICE);
        breaker.recordFailure(SERVICE);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isTrue();
    }

    @ParameterizedTest(name = "after {0} ms -> allowed={1}")
    @CsvSource({
            "9999,  false",
            "10000, true"
    })
    void shouldLetTrialRequestThroughWhenOpenDurationPassed(long elapsedMs, boolean expected) {
        // Arrange
        openCircuit();
        clock.advance(Duration.ofMillis(elapsedMs));

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isEqualTo(expected);
    }

    @Test
    void shouldRejectRequestWhenTrialRequestIsInProgress() {
        // Arrange
        openCircuit();
        clock.advance(OPEN_DURATION);
        breaker.allowRequest(SERVICE);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldCloseWhenTrialRequestSucceeds() {
        // Arrange
        openCircuit();
        clock.advance(OPEN_DURATION);
        breaker.allowRequest(SERVICE);
        breaker.recordSuccess(SERVICE);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldReopenWhenTrialRequestFails() {
        // Arrange
        openCircuit();
        clock.advance(OPEN_DURATION);
        breaker.allowRequest(SERVICE);
        breaker.recordFailure(SERVICE);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldAllowNextTrialWhenTrialRequestIsReleased() {
        // Arrange
        openCircuit();
        clock.advance(OPEN_DURATION);
        breaker.allowRequest(SERVICE);
        breaker.releaseRequest(SERVICE);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowRequestWhenOnlyAnotherServiceCircuitIsOpen() {
        // Arrange
        openCircuit();

        // Act
        boolean allowed = breaker.allowRequest("authorization");

        // Assert
        assertThat(allowed).isTrue();
    }

    private void openCircuit() {
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
