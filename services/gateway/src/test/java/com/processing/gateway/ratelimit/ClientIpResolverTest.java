package com.processing.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final InetSocketAddress REMOTE = new InetSocketAddress("192.168.1.10", 54321);

    private final ClientIpResolver resolver = new ClientIpResolver();

    @ParameterizedTest(name = "X-Forwarded-For=''{0}'' -> {1}")
    @CsvSource(delimiter = '|', value = {
            "10.0.0.1                    | 10.0.0.1",
            "' 10.0.0.1 , 172.16.0.1'    | 10.0.0.1",
            "10.0.0.1,172.16.0.1,8.8.8.8 | 10.0.0.1"
    })
    void shouldTakeFirstAddressWhenForwardedForHeaderIsPresent(String header, String expected) {
        // Arrange
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/transaction")
                .header("X-Forwarded-For", header)
                .header("X-Real-IP", "10.9.9.9")
                .remoteAddress(REMOTE));

        // Act
        String ip = resolver.resolve(exchange);

        // Assert
        assertThat(ip).isEqualTo(expected);
    }

    @Test
    void shouldUseRealIpHeaderWhenForwardedForIsBlank() {
        // Arrange
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/transaction")
                .header("X-Forwarded-For", "  ")
                .header("X-Real-IP", " 10.9.9.9 ")
                .remoteAddress(REMOTE));

        // Act
        String ip = resolver.resolve(exchange);

        // Assert
        assertThat(ip).isEqualTo("10.9.9.9");
    }

    @Test
    void shouldUseRemoteAddressWhenProxyHeadersAreMissing() {
        // Arrange
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/transaction")
                .remoteAddress(REMOTE));

        // Act
        String ip = resolver.resolve(exchange);

        // Assert
        assertThat(ip).isEqualTo("192.168.1.10");
    }

    @Test
    void shouldReturnUnknownWhenAddressCannotBeResolved() {
        // Arrange
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/transaction")
                .remoteAddress(InetSocketAddress.createUnresolved("terminal-host", 54321)));

        // Act
        String ip = resolver.resolve(exchange);

        // Assert
        assertThat(ip).isEqualTo("unknown");
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }
}
