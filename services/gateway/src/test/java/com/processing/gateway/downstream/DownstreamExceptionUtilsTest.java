package com.processing.gateway.downstream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownstreamExceptionUtilsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableErrors")
    void shouldReportUnavailableWhenChainContainsNetworkError(Throwable error) {
        // Act
        boolean unavailable = DownstreamExceptionUtils.isDownstreamUnavailable(error);

        // Assert
        assertThat(unavailable).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("otherErrors")
    void shouldNotReportUnavailableWhenChainHasNoNetworkError(Throwable error) {
        // Act
        boolean unavailable = DownstreamExceptionUtils.isDownstreamUnavailable(error);

        // Assert
        assertThat(unavailable).isFalse();
    }

    @Test
    void shouldRethrowSameExceptionWhenItIsIoException() {
        // Arrange
        IOException error = new IOException("broken pipe");

        // Act + Assert
        assertThatThrownBy(() -> DownstreamExceptionUtils.rethrow(error)).isSameAs(error);
    }

    @Test
    void shouldRethrowSameExceptionWhenItIsRuntimeException() {
        // Arrange
        IllegalStateException error = new IllegalStateException("bad state");

        // Act + Assert
        assertThatThrownBy(() -> DownstreamExceptionUtils.rethrow(error)).isSameAs(error);
    }

    @Test
    void shouldWrapIntoIoExceptionWhenExceptionIsChecked() {
        // Arrange
        Exception error = new Exception("checked");

        // Act + Assert
        assertThatThrownBy(() -> DownstreamExceptionUtils.rethrow(error))
                .isInstanceOf(IOException.class)
                .hasCause(error);
    }

    static Stream<Arguments> unavailableErrors() {
        return Stream.of(
                Arguments.of(new ResourceAccessException("I/O error")),
                Arguments.of(new ConnectException("Connection refused")),
                Arguments.of(new SocketTimeoutException("Read timed out")),
                Arguments.of(new HttpTimeoutException("timeout")),
                Arguments.of(new HttpConnectTimeoutException("connect timeout")),
                Arguments.of(new IllegalStateException("wrapped", new ConnectException("Connection refused"))),
                Arguments.of(new ExecutionException(new RuntimeException(new SocketTimeoutException("deep")))));
    }

    static Stream<Arguments> otherErrors() {
        return Stream.of(
                Arguments.of((Throwable) null),
                Arguments.of(new IllegalArgumentException("validation")),
                Arguments.of(new IOException("broken pipe")),
                Arguments.of(new RuntimeException("wrapped", new IllegalStateException("inner"))));
    }
}
