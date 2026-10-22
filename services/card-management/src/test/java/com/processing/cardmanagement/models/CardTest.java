package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

    private static final String PAN = "4000001234567890";
    private static final String OTHER_PAN = "4000009999999999";
    private static final String RRN = "612345678901";
    private static final BigDecimal BALANCE = new BigDecimal("100000");
    private static final BigDecimal AMOUNT = new BigDecimal("30000");

    @ParameterizedTest(name = "amount {0}, balance 100000")
    @CsvSource({"1", "99999", "100000"})
    void shouldStartReservationWhenCardIsActiveAndBalanceIsEnough(BigDecimal amount) {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act
        Reservation reservation = card.startReservation(amount, RRN);

        // Assert
        assertThat(reservation.pan()).isEqualTo(PAN);
        assertThat(reservation.reservationAmount()).isEqualByComparingTo(amount);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void shouldThrowInsufficientFundsWhenAmountExceedsBalanceByOneKopeck() {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act + Assert
        assertThatThrownBy(() -> card.startReservation(new BigDecimal("100001"), RRN))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = CardStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldRejectReservationWhenCardIsNotActive(CardStatus status) {
        // Arrange
        Card card = card(status, BALANCE);

        // Act + Assert
        assertThatThrownBy(() -> card.startReservation(BigDecimal.ONE, RRN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(status.name());
    }

    @Test
    void shouldDecreaseBalanceWhenReservationIsApplied() {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);
        Reservation reservation = new Reservation(PAN, AMOUNT, RRN);

        // Act
        Card reserved = card.withReservation(reservation);

        // Assert
        assertThat(reserved.availableBalance()).isEqualByComparingTo(BALANCE.subtract(AMOUNT));
    }

    @Test
    void shouldRejectReservationWhenPanDiffers() {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);
        Reservation reservation = new Reservation(OTHER_PAN, AMOUNT, RRN);

        // Act + Assert
        assertThatThrownBy(() -> card.withReservation(reservation)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldIncreaseBalanceWhenRollbackIsApplied() {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act
        Card rolledBack = card.withRollback(rollback(PAN, AMOUNT));

        // Assert
        assertThat(rolledBack.availableBalance()).isEqualByComparingTo(BALANCE.add(AMOUNT));
    }

    @Test
    void shouldRejectRollbackWhenPanDiffers() {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act + Assert
        assertThatThrownBy(() -> card.withRollback(rollback(OTHER_PAN, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMarkCardAsDeletedWhenItIsNotDeletedYet() {
        // Arrange
        Card card = card(CardStatus.BLOCKED, BALANCE);

        // Act
        Card deleted = card.deleted();

        // Assert
        assertThat(deleted.status()).isEqualTo(CardStatus.DELETED);
    }

    @Test
    void shouldRejectDeletionWhenCardIsAlreadyDeleted() {
        // Arrange
        Card card = card(CardStatus.DELETED, BALANCE);

        // Act + Assert
        assertThatThrownBy(card::deleted).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "daily {0}, monthly {1}")
    @CsvSource({
            "0,      0",
            "1000,   1000",
            "1000,   30000"
    })
    void shouldUpdateDataWhenLimitsAreNonNegativeAndMonthlyIsNotLessThanDaily(BigDecimal daily, BigDecimal monthly) {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act
        Card updated = card.withData(CardStatus.BLOCKED, daily, monthly, BigDecimal.TEN);

        // Assert
        assertThat(updated.status()).isEqualTo(CardStatus.BLOCKED);
        assertThat(updated.dailyLimit()).isEqualByComparingTo(daily);
        assertThat(updated.monthlyLimit()).isEqualByComparingTo(monthly);
        assertThat(updated.availableBalance()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @ParameterizedTest(name = "daily {0}, monthly {1}")
    @CsvSource({
            "-1,     1000",
            "1000,   -1",
            "1001,   1000"
    })
    void shouldRejectDataWhenLimitIsNegativeOrMonthlyIsLessThanDaily(BigDecimal daily, BigDecimal monthly) {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act + Assert
        assertThatThrownBy(() -> card.withData(CardStatus.ACTIVE, daily, monthly, BALANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTakePanAndIssuerFromArgumentsWhenCreatingCardFromDraft() {
        // Arrange
        CardDraft draft = new CardDraft("400000", "IVAN IVANOV", CardStatus.ACTIVE, "643",
                new BigDecimal("1000"), new BigDecimal("30000"), BALANCE);

        // Act
        Card card = Card.fromDraft(PAN, "ISS001", 3, draft);

        // Assert
        assertThat(card.pan()).isEqualTo(PAN);
        assertThat(card.issuerId()).isEqualTo("ISS001");
        assertThat(card.availableBalance()).isEqualByComparingTo(BALANCE);
    }

    @Test
    void shouldKeepDataButReplacePanAndIdWhenCopiedWithPan() {
        // Arrange
        Card card = card(CardStatus.ACTIVE, BALANCE);

        // Act
        Card copy = card.copyWithPan(OTHER_PAN);

        // Assert
        assertThat(copy.pan()).isEqualTo(OTHER_PAN);
        assertThat(copy.id()).isNotEqualTo(card.id());
        assertThat(copy.availableBalance()).isEqualByComparingTo(BALANCE);
    }

    private static Card card(CardStatus status, BigDecimal balance) {
        return new Card(UUID.fromString("00000000-0000-0000-0000-000000000001"), PAN, "400000", "IVAN IVANOV",
                YearMonth.of(2029, 10), status, "643", new BigDecimal("15000000"), new BigDecimal("300000000"),
                balance, "ISS001", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static ReservationRollback rollback(String pan, BigDecimal amount) {
        return new ReservationRollback(UUID.randomUUID(), UUID.randomUUID(), pan, amount, RRN,
                Instant.parse("2026-10-21T10:15:30Z"));
    }
}
