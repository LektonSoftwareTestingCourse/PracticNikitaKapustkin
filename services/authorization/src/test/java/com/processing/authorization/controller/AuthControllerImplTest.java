package com.processing.authorization.controller;

import com.processing.authorization.services.AuthServiceImpl;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerImplTest {

    private static final Instant REQUEST_TIME = Instant.parse("2026-10-21T10:15:30Z");

    @Mock
    private AuthServiceImpl authService;

    private AuthControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new AuthControllerImpl(authService);
    }

    @Test
    void shouldRespondOkWhenAuthorizationIsApproved() {
        // Arrange
        AuthorizationRequest request = authorizationRequest();
        when(authService.authorize(eq(request), any()))
                .thenReturn(AuthorizationResponse.approved(request, "612345678901", "A1B2C3", REQUEST_TIME));

        // Act
        ResponseEntity<AuthorizationResponse> response = controller.authorize(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource({
            "CARD_NOT_FOUND,       14, 404",
            "SERVICE_UNAVAILABLE,  96, 503",
            "RESERVATION_FAILED,   96, 503",
            "INSUFFICIENT_FUNDS,   51, 422",
            "CARD_EXPIRED,         54, 403",
            "CARD_BLOCKED,         05, 403",
            "CARD_INACTIVE,        05, 403",
            "EXCEEDS_AMOUNT_LIMIT, 61, 400",
            "DB_UNAVAILABLE,       96, 400",
            "UNKNOWN_REASON,       05, 400"
    })
    void shouldRespondWithMappedHttpStatusWhenAuthorizationIsDeclined(String reason, String code, int httpStatus) {
        // Arrange
        AuthorizationRequest request = authorizationRequest();
        when(authService.authorize(eq(request), any()))
                .thenReturn(AuthorizationResponse.declined(request, reason, code, REQUEST_TIME));

        // Act
        ResponseEntity<AuthorizationResponse> response = controller.authorize(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(httpStatus);
        assertThat(response.getBody().declineReason()).isEqualTo(reason);
    }

    @Test
    void shouldRespondOkWhenRollbackIsApproved() {
        // Arrange
        RollbackRequest request = rollbackRequest();
        when(authService.rollback(eq(request), any())).thenReturn(RollbackResponse.approved(request, REQUEST_TIME));

        // Act
        ResponseEntity<RollbackResponse> response = controller.rollback(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource({
            "TRANSACTION_NOT_FOUND, 14, 404",
            "CARD_NOT_FOUND,        14, 404",
            "ALREADY_ROLLED_BACK,   05, 409",
            "SERVICE_UNAVAILABLE,   96, 503",
            "ROLLBACK_FAILED,       96, 503",
            "UNKNOWN_REASON,        05, 400"
    })
    void shouldRespondWithMappedHttpStatusWhenRollbackIsDeclined(String reason, String code, int httpStatus) {
        // Arrange
        RollbackRequest request = rollbackRequest();
        when(authService.rollback(eq(request), any()))
                .thenReturn(RollbackResponse.declined(request, reason, code, REQUEST_TIME));

        // Act
        ResponseEntity<RollbackResponse> response = controller.rollback(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(httpStatus);
    }

    private static AuthorizationRequest authorizationRequest() {
        return new AuthorizationRequest(
                "0100", "000001", "4000001234567890", "000000", new BigDecimal("150000"), "643", REQUEST_TIME,
                "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", "ISS001");
    }

    private static RollbackRequest rollbackRequest() {
        return new RollbackRequest("612345678901", "4000001234567890", new BigDecimal("150000"));
    }
}
