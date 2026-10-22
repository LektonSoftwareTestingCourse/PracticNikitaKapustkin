package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.config.SwitchProperties;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthorizationClientTest {

    private static final String AUTH_URL = "http://authorization";
    private static final String RRN = "612345678901";
    private static final String APPROVED_JSON = """
            {"mti":"0110","stan":"000001","rrn":"612345678901","authCode":"A1B2C3",
             "responseCode":"00","status":"APPROVED","declineReason":null,"processingTimeMs":42}
            """;
    private static final String BLOCKED_JSON = """
            {"mti":"0110","stan":"000001","rrn":null,"authCode":null,
             "responseCode":"05","status":"DECLINED","declineReason":"CARD_BLOCKED","processingTimeMs":7}
            """;
    private static final String UNKNOWN_STATUS_JSON = """
            {"mti":"0110","stan":"000001","responseCode":"00","status":"PENDING"}
            """;

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("authorization");

    @Test
    void shouldReturnApprovalWhenAuthorizationApproves() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        expectAuthorize().andRespond(withSuccess(APPROVED_JSON, MediaType.APPLICATION_JSON));

        // Act
        AuthorizationResponse response = client.authorize(request());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.rrn()).isEqualTo(RRN);
    }

    @Test
    void shouldReturnDeclineFromBodyWhenAuthorizationRespondsWithErrorStatus() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        expectAuthorize().andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BLOCKED_JSON));

        // Act
        AuthorizationResponse response = client.authorize(request());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("CARD_BLOCKED");
    }

    @Test
    void shouldThrowAuthorizationExceptionWhenResponseStatusIsNeitherApprovedNorDeclined() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        expectAuthorize().andRespond(withSuccess(UNKNOWN_STATUS_JSON, MediaType.APPLICATION_JSON));

        // Act + Assert
        assertThatThrownBy(() -> client.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Invalid response from Authorization");
    }

    @Test
    void shouldRetryAndReturnApprovalWhenFirstAttemptFails() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(2);
        expectAuthorize().andRespond(withServerError());
        expectAuthorize().andRespond(withSuccess(APPROVED_JSON, MediaType.APPLICATION_JSON));

        // Act
        AuthorizationResponse response = client.authorize(request());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
    }

    @Test
    void shouldThrowWithoutCallingAuthorizationWhenCircuitBreakerIsOpen() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        circuitBreaker.transitionToOpenState();

        // Act + Assert
        assertThatThrownBy(() -> client.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Circuit breaker open");
    }

    @Test
    void shouldReturnRollbackResultWhenAuthorizationRollsBack() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        expectRollback().andRespond(withSuccess(
                "{\"rrn\":\"612345678901\",\"responseCode\":\"00\",\"status\":\"APPROVED\"}",
                MediaType.APPLICATION_JSON));

        // Act
        RollbackResponse response = client.rollback(request(), RRN);

        // Assert
        assertThat(response.status()).isEqualTo(RollbackResponse.STATUS_APPROVED);
    }

    @Test
    void shouldReturnRollbackDeclineWhenReservationIsAlreadyRolledBack() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        expectRollback().andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"rrn\":\"612345678901\",\"responseCode\":\"05\",\"status\":\"DECLINED\","
                        + "\"declineReason\":\"ALREADY_ROLLED_BACK\"}"));

        // Act
        RollbackResponse response = client.rollback(request(), RRN);

        // Assert
        assertThat(response.declineReason()).isEqualTo("ALREADY_ROLLED_BACK");
    }

    @Test
    void shouldReturnNullWhenRollbackResponseIsInvalid() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        expectRollback().andRespond(withServerError());

        // Act
        RollbackResponse response = client.rollback(request(), RRN);

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void shouldReportOkWhenAuthorizationHealthEndpointResponds() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        server.expect(requestTo(AUTH_URL + "/health")).andRespond(withSuccess());

        // Act
        String health = client.checkHealth();

        // Assert
        assertThat(health).isEqualTo("ok");
    }

    @Test
    void shouldReportDownWhenAuthorizationHealthEndpointFails() {
        // Arrange
        AuthorizationClient client = clientWithAttempts(1);
        server.expect(requestTo(AUTH_URL + "/health")).andRespond(withServerError());

        // Act
        String health = client.checkHealth();

        // Assert
        assertThat(health).isEqualTo("down");
    }

    private AuthorizationClient clientWithAttempts(int maxAttempts) {
        Retry retry = Retry.of("authorization", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(1))
                .build());
        return new AuthorizationClient(properties(maxAttempts), restClientBuilder.build(), retry, circuitBreaker);
    }

    private ResponseActions expectAuthorize() {
        return server.expect(requestTo(AUTH_URL + "/api/internal/authorize")).andExpect(method(HttpMethod.POST));
    }

    private ResponseActions expectRollback() {
        return server.expect(requestTo(AUTH_URL + "/api/internal/rollback")).andExpect(method(HttpMethod.POST));
    }

    private static SwitchProperties properties(int maxAttempts) {
        return new SwitchProperties("1.0.0", null, AUTH_URL, null, null, null,
                new SwitchProperties.RetryProperties(maxAttempts, null), null);
    }

    private static AuthorizationRequest request() {
        return new AuthorizationRequest(
                "0100", "000001", "4000001234567890", "000000", new BigDecimal("150000"), "643",
                Instant.parse("2026-10-21T10:15:30Z"), "TERM0001", "POS", "MERCH0000000001", "5411",
                "ACQ001", "ISS001");
    }
}
