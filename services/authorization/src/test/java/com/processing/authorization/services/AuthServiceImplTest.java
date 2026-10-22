package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.AuthServiceAuthorizeEvent;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.GetCardException;
import com.processing.authorization.exceptions.InsufficientFundsException;
import com.processing.authorization.exceptions.InternalCardManagerException;
import com.processing.authorization.exceptions.InvalidGetCardRequestException;
import com.processing.authorization.exceptions.InvalidReserveRequestException;
import com.processing.authorization.exceptions.InvalidRollbackRequestException;
import com.processing.authorization.exceptions.PaymentRequiredException;
import com.processing.authorization.exceptions.ReserveException;
import com.processing.authorization.exceptions.RollbackConflictException;
import com.processing.authorization.exceptions.RollbackFailureException;
import com.processing.authorization.exceptions.ServiceUnavailableException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PAN = "4000001234567890";
    private static final String RRN = "612345678901";
    private static final BigDecimal AMOUNT = new BigDecimal("150000");
    private static final BigDecimal BALANCE = new BigDecimal("1000000");
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-10-21T10:15:30Z");
    private static final Instant REQUEST_INPUT_TIME = Instant.parse("2026-10-21T10:15:31Z");
    private static final YearMonth TRANSMISSION_MONTH = YearMonth.of(2026, 10);
    private static final YearMonth VALID_EXPIRY = YearMonth.of(2029, 10);

    @Mock
    private LimitUsageRepository limitUsageRepository;

    @Mock
    private AuthorizationEventNotifier eventNotifier;

    @Mock
    private BinLookupClient binLookupClient;

    @Mock
    private CardManagementClient cardManagementClient;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(limitUsageRepository, eventNotifier, binLookupClient, cardManagementClient);
    }

    @Test
    void shouldApproveWhenCardIsActiveAndAmountIsWithinBalanceAndLimits() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.rrn()).matches("\\d{12}");
        assertThat(response.authCode()).matches("[0-9A-Z]{6}");
    }

    @Test
    void shouldReserveAmountWithGeneratedRrnWhenTransactionIsApproved() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        verify(cardManagementClient).reserve(AMOUNT, response.rrn(), PAN);
    }

    @Test
    void shouldNotifyAboutApprovalWhenTransactionIsApproved() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        verify(eventNotifier).notify(any(AuthServiceAuthorizeEvent.class));
    }

    @Test
    void shouldBuildRrnFromSequenceBlockWhenFirstRrnIsGenerated() {
        // Arrange
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(5L);

        // Act
        String rrn = authService.generateRRN();

        // Assert
        assertThat(rrn).hasSize(12).endsWith("00005000");
    }

    @ParameterizedTest(name = "{0} -> {1}, code {2}")
    @CsvSource({
            "INACTIVE, CARD_INACTIVE, 05",
            "BLOCKED,  CARD_BLOCKED,  05",
            "EXPIRED,  CARD_EXPIRED,  54"
    })
    void shouldDeclineWithStatusReasonWhenCardIsNotActive(CardModelStatus status, String reason, String code) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(status, VALID_EXPIRY, BALANCE));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
        assertThat(response.responseCode()).isEqualTo(code);
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = CardModelStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldNotTouchLimitsOrFundsWhenCardIsNotActive(CardModelStatus status) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(status, VALID_EXPIRY, BALANCE));

        // Act
        authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        verify(limitUsageRepository, never()).upsertLimitUsage(any(), any(), any(), any(), any());
        verify(cardManagementClient, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldDeclineWithUnknownReasonWhenCardStatusIsMissing() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(null, VALID_EXPIRY, BALANCE));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo("UNKNOWN_REASON");
        assertThat(response.responseCode()).isEqualTo("05");
    }

    @ParameterizedTest(name = "expiry {0}, transmission {1}")
    @CsvSource({
            "2026-10, 2026-10-21T10:15:30Z",
            "2026-10, 2026-10-31T23:59:59Z",
            "2026-11, 2026-10-21T10:15:30Z"
    })
    void shouldPassExpiryCheckWhenCardIsValidThroughTransmissionMonth(YearMonth expiry, Instant transmission) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, expiry, BALANCE));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, transmission), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @ParameterizedTest(name = "expiry {0}, transmission {1}")
    @CsvSource({
            "2026-09, 2026-10-21T10:15:30Z",
            "2026-10, 2026-11-01T00:00:00Z"
    })
    void shouldDeclineAsExpiredWhenTransmissionIsAfterExpiryMonth(YearMonth expiry, Instant transmission) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, expiry, BALANCE));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, transmission), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo("CARD_EXPIRED");
        assertThat(response.responseCode()).isEqualTo("54");
    }

    @ParameterizedTest(name = "amount {0}, balance 150000")
    @CsvSource({"149999", "150000"})
    void shouldApproveWhenAmountDoesNotExceedBalance(BigDecimal amount) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, VALID_EXPIRY, AMOUNT));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(amount), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @Test
    void shouldDeclineWithInsufficientFundsWhenAmountExceedsBalanceByOneKopeck() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, VALID_EXPIRY, AMOUNT));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT.add(BigDecimal.ONE)), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.responseCode()).isEqualTo("51");
    }

    @Test
    void shouldDeclineWithLimitExceededWhenLimitUsageIsNotUpdated() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo("EXCEEDS_AMOUNT_LIMIT");
        assertThat(response.responseCode()).isEqualTo("61");
    }

    @Test
    void shouldNotReserveFundsWhenLimitIsExceeded() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);

        // Act
        authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        verify(cardManagementClient, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldPassTransmissionDateAndCardLimitsWhenCheckingLimits() {
        // Arrange
        CardModel card = activeCard();
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(limitUsageRepository.upsertLimitUsage(PAN, TRANSMISSION_MONTH.atDay(21), AMOUNT,
                card.dailyLimit(), card.monthlyLimit())).thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2026-10-21T00:00:00Z, 2026-10-21",
            "2026-10-21T23:59:59Z, 2026-10-21",
            "2026-10-22T00:00:00Z, 2026-10-22",
            "2026-10-21T21:30:00Z, 2026-10-21"
    })
    void shouldUseUtcTransmissionDateWhenCountingDailyLimit(Instant transmission, LocalDate expectedDate) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        ArgumentCaptor<LocalDate> limitDate = ArgumentCaptor.forClass(LocalDate.class);
        when(limitUsageRepository.upsertLimitUsage(any(), limitDate.capture(), any(), any(), any())).thenReturn(1);

        // Act
        authService.authorize(request(AMOUNT, transmission), REQUEST_INPUT_TIME);

        // Assert
        assertThat(limitDate.getValue()).isEqualTo(expectedDate);
    }

    @Test
    void shouldRetryLimitUpdateOnceWhenConcurrentInsertCausesDuplicateKey() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"))
                .thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @Test
    void shouldDeclineWithDbUnavailableWhenLimitUpdateFailsAfterRetry() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"))
                .thenThrow(new IllegalStateException("connection lost"));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo("DB_UNAVAILABLE");
        assertThat(response.responseCode()).isEqualTo("96");
    }

    @Test
    void shouldDeclineWithDbUnavailableWhenLimitStorageFails() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("connection lost"));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo("DB_UNAVAILABLE");
    }

    @ParameterizedTest(name = "{0} -> {1}, code {2}")
    @MethodSource("getCardFailures")
    void shouldDeclineWhenCardCannotBeObtained(RuntimeException failure, String reason, String code) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenThrow(failure);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
        assertThat(response.responseCode()).isEqualTo(code);
    }

    @ParameterizedTest(name = "{0} -> {1}, code {2}")
    @MethodSource("reserveFailures")
    void shouldDeclineWhenReservationFails(RuntimeException failure, String reason, String code) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        doThrow(failure).when(cardManagementClient).reserve(eq(AMOUNT), any(), eq(PAN));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
        assertThat(response.responseCode()).isEqualTo(code);
    }

    @Test
    void shouldApproveRollbackWhenCardManagementReturnsFunds() {
        // Act
        RollbackResponse response = authService.rollback(rollbackRequest(), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.responseCode()).isEqualTo("00");
        verify(cardManagementClient).rollback(rollbackRequest());
    }

    @ParameterizedTest(name = "{0} -> {1}, code {2}")
    @MethodSource("rollbackFailures")
    void shouldDeclineRollbackWhenCardManagementFails(RuntimeException failure, String reason, String code) {
        // Arrange
        doThrow(failure).when(cardManagementClient).rollback(any());

        // Act
        RollbackResponse response = authService.rollback(rollbackRequest(), REQUEST_INPUT_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
        assertThat(response.responseCode()).isEqualTo(code);
    }

    static Stream<Arguments> getCardFailures() {
        return Stream.of(
                Arguments.of(new CardNotFoundException("not found"), "CARD_NOT_FOUND", "14"),
                Arguments.of(new InvalidGetCardRequestException("bad pan"), "CARD_NOT_FOUND", "14"),
                Arguments.of(new ServiceUnavailableException("503"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new ResourceAccessException("timeout"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new InternalCardManagerException("500"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new GetCardException("418"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new PaymentRequiredException("402"), "CARD_BLOCKED", "05"),
                Arguments.of(new IllegalStateException("unexpected"), "UNKNOWN_REASON", "05"));
    }

    static Stream<Arguments> reserveFailures() {
        return Stream.of(
                Arguments.of(new CardNotFoundException("not found"), "CARD_NOT_FOUND", "14"),
                Arguments.of(new InvalidReserveRequestException("bad request"), "CARD_NOT_FOUND", "14"),
                Arguments.of(new ServiceUnavailableException("503"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new ResourceAccessException("timeout"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new InternalCardManagerException("500"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new InsufficientFundsException("402"), "INSUFFICIENT_FUNDS", "51"),
                Arguments.of(new ReserveException("409"), "RESERVATION_FAILED", "96"),
                Arguments.of(new IllegalStateException("unexpected"), "UNKNOWN_REASON", "05"));
    }

    static Stream<Arguments> rollbackFailures() {
        return Stream.of(
                Arguments.of(new CardNotFoundException("not found"), "TRANSACTION_NOT_FOUND", "14"),
                Arguments.of(new InvalidRollbackRequestException("bad request"), "TRANSACTION_NOT_FOUND", "14"),
                Arguments.of(new ServiceUnavailableException("503"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new ResourceAccessException("timeout"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new InternalCardManagerException("500"), "SERVICE_UNAVAILABLE", "96"),
                Arguments.of(new RollbackConflictException("409"), "ALREADY_ROLLED_BACK", "05"),
                Arguments.of(new RollbackFailureException("422"), "ROLLBACK_FAILED", "96"),
                Arguments.of(new IllegalStateException("unexpected"), "UNKNOWN_REASON", "05"));
    }

    private static AuthorizationRequest request(BigDecimal amount) {
        return request(amount, TRANSMISSION_TIME);
    }

    private static AuthorizationRequest request(BigDecimal amount, Instant transmissionTime) {
        return new AuthorizationRequest(
                "0100", "000001", PAN, "000000", amount, "643", transmissionTime,
                "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", "ISS001");
    }

    private static RollbackRequest rollbackRequest() {
        return new RollbackRequest(RRN, PAN, AMOUNT);
    }

    private static CardModel activeCard() {
        return card(CardModelStatus.ACTIVE, VALID_EXPIRY, BALANCE);
    }

    private static CardModel card(CardModelStatus status, YearMonth expiry, BigDecimal availableBalance) {
        return new CardModel(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), PAN, "400000", "IVAN IVANOV",
                expiry, status, "643", new BigDecimal("15000000"), new BigDecimal("300000000"),
                availableBalance, "ISS001", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
