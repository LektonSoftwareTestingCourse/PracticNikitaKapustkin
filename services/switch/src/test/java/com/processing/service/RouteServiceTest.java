package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final String PAN = "4000001234567890";
    private static final String ISSUER_ID = "ISS001";
    private static final String STAN = "000001";
    private static final String RRN = "612345678901";
    private static final BigDecimal AMOUNT = new BigDecimal("150000");
    private static final BigDecimal ACQUIRING_FEE = new BigDecimal("2250.00");
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-10-21T10:15:30Z");

    @Mock
    private RoutingService routingService;

    @Mock
    private AuthorizationClient authorizationClient;

    @Mock
    private AcquiringFeeClient acquiringFeeClient;

    @Mock
    private LoggerClient loggerClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(routingService, authorizationClient, acquiringFeeClient, loggerClient);
    }

    @Test
    void shouldReturnApprovalWhenAuthorizationApprovesAndTransactionIsPublished() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(approved());
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request(PAN));

        // Assert
        assertThat(response).isEqualTo(approved());
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldPublishTransactionWithIssuerIdAndAcquiringFeeWhenApproved() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(approved());
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(ACQUIRING_FEE);
        ArgumentCaptor<TransactionRequest> logged = ArgumentCaptor.forClass(TransactionRequest.class);
        when(loggerClient.log(logged.capture())).thenReturn(true);

        // Act
        routeService.route(request(PAN));

        // Assert
        TransactionRequest published = logged.getValue();
        assertThat(published.issuerId()).isEqualTo(ISSUER_ID);
        assertThat(published.acquiringFee()).isEqualByComparingTo(ACQUIRING_FEE);
        assertThat(published.status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(published.rrn()).isEqualTo(RRN);
    }

    @Test
    void shouldPublishZeroProcessingTimeWhenAuthorizationDoesNotReportIt() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(approvedWithoutProcessingTime());
        ArgumentCaptor<TransactionRequest> logged = ArgumentCaptor.forClass(TransactionRequest.class);
        when(loggerClient.log(logged.capture())).thenReturn(true);

        // Act
        routeService.route(request(PAN));

        // Assert
        assertThat(logged.getValue().processingTimeMs()).isZero();
    }

    @ParameterizedTest(name = "pan=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"4999991234567890", "40000"})
    void shouldDeclineWithCardNotFoundWhenBinIsUnknown(String pan) {
        // Arrange
        when(routingService.getIssuerIdByPan(pan)).thenThrow(new UnknownBinException("unknown"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request(pan));

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
    }

    @Test
    void shouldDeclineWithGeneralCodeWhenAuthorizationIsUnavailable() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenThrow(new AuthorizationException(STAN, 3, "timeout"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request(PAN));

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_DECLINED_GENERAL);
    }

    @Test
    void shouldRollbackReservationWhenApprovedTransactionIsNotPublished() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(approved());
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        routeService.route(request(PAN));

        // Assert
        verify(authorizationClient).rollback(any(AuthorizationRequest.class), eq(RRN));
    }

    @ParameterizedTest(name = "rollback result: {0}")
    @MethodSource("rollbackResults")
    void shouldReturnSystemErrorWhenApprovedTransactionIsNotPublished(String description, RollbackResponse rollback) {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(approved());
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(rollback);

        // Act
        AuthorizationResponse response = routeService.route(request(PAN));

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldReturnOriginalDeclineWithoutRollbackWhenDeclinedTransactionIsNotPublished() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(declined());
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        AuthorizationResponse response = routeService.route(request(PAN));

        // Assert
        assertThat(response).isEqualTo(declined());
        verify(authorizationClient, never()).rollback(any(), any());
    }

    static Stream<Arguments> rollbackResults() {
        return Stream.of(
                Arguments.of("approved", rollbackApproved()),
                Arguments.of("declined", new RollbackResponse(RRN, "05", "DECLINED", "ALREADY_ROLLED_BACK", 3)),
                Arguments.of("no response", null));
    }

    private static AuthorizationRequest request(String pan) {
        return new AuthorizationRequest(
                "0100", STAN, pan, "000000", AMOUNT, "643", TRANSMISSION_TIME,
                "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null);
    }

    private static AuthorizationResponse approved() {
        return new AuthorizationResponse("0110", STAN, RRN, "A1B2C3", "00", "APPROVED", null, 42);
    }

    private static AuthorizationResponse approvedWithoutProcessingTime() {
        return new AuthorizationResponse("0110", STAN, RRN, "A1B2C3", "00", "APPROVED", null, null);
    }

    private static AuthorizationResponse declined() {
        return new AuthorizationResponse("0110", STAN, null, null, "05", "DECLINED", "CARD_BLOCKED", 17);
    }

    private static RollbackResponse rollbackApproved() {
        return new RollbackResponse(RRN, "00", "APPROVED", null, 5);
    }
}
