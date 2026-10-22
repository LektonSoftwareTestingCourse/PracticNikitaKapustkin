package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.RollbackAlreadySatisfiedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final String PAN = "4000001234567890";
    private static final String RRN = "612345678901";
    private static final BigDecimal AMOUNT = new BigDecimal("150000");

    @Test
    void shouldStartRollbackLinkedToReservationWhenReservationIsActive() {
        // Arrange
        Reservation reservation = new Reservation(PAN, AMOUNT, RRN);

        // Act
        ReservationRollback rollback = reservation.startRollback(AMOUNT);

        // Assert
        assertThat(rollback.reservationId()).isEqualTo(reservation.id());
        assertThat(rollback.pan()).isEqualTo(PAN);
        assertThat(rollback.rrn()).isEqualTo(RRN);
        assertThat(rollback.rollbackAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void shouldRejectRollbackWhenReservationIsAlreadyRolledBack() {
        // Arrange
        Reservation reservation = rolledBackReservation();

        // Act + Assert
        assertThatThrownBy(() -> reservation.startRollback(AMOUNT))
                .isInstanceOf(RollbackAlreadySatisfiedException.class);
    }

    @Test
    void shouldMarkReservationAsRolledBackWhenRollbackBelongsToIt() {
        // Arrange
        Reservation reservation = new Reservation(PAN, AMOUNT, RRN);
        ReservationRollback rollback = reservation.startRollback(AMOUNT);

        // Act
        Reservation rolledBack = reservation.rolledBack(rollback);

        // Assert
        assertThat(rolledBack.status()).isEqualTo(ReservationStatus.ROLLED_BACK);
        assertThat(rolledBack.id()).isEqualTo(reservation.id());
    }

    @Test
    void shouldRejectRollbackWhenItBelongsToAnotherReservation() {
        // Arrange
        Reservation reservation = new Reservation(PAN, AMOUNT, RRN);
        ReservationRollback foreignRollback = new Reservation(PAN, AMOUNT, RRN).startRollback(AMOUNT);

        // Act + Assert
        assertThatThrownBy(() -> reservation.rolledBack(foreignRollback))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Reservation rolledBackReservation() {
        Instant createdAt = Instant.parse("2026-10-21T10:15:30Z");
        return new Reservation(UUID.randomUUID(), PAN, AMOUNT, RRN, ReservationStatus.ROLLED_BACK,
                createdAt, createdAt);
    }
}
