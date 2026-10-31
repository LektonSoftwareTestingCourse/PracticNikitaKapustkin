package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.CardEventNotifier;
import com.processing.cardmanagement.events.CardServiceCreationEvent;
import com.processing.cardmanagement.events.CardServiceDeletionEvent;
import com.processing.cardmanagement.events.CardServiceReserveEvent;
import com.processing.cardmanagement.events.CardServiceRollbackEvent;
import com.processing.cardmanagement.exceptions.CardNotFoundException;
import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import com.processing.cardmanagement.exceptions.MassiveCardCreationCollisionException;
import com.processing.cardmanagement.exceptions.ReservationAlreadyExistsException;
import com.processing.cardmanagement.exceptions.ReservationNotFoundException;
import com.processing.cardmanagement.exceptions.RollbackAlreadySatisfiedException;
import com.processing.cardmanagement.exceptions.TooLargeLimitException;
import com.processing.cardmanagement.models.Card;
import com.processing.cardmanagement.models.CardDraft;
import com.processing.cardmanagement.models.CardStatus;
import com.processing.cardmanagement.models.Reservation;
import com.processing.cardmanagement.models.ReservationRollback;
import com.processing.cardmanagement.models.ReservationStatus;
import com.processing.cardmanagement.options.CardServiceDefaults;
import com.processing.cardmanagement.options.CardServiceSettings;
import com.processing.cardmanagement.repositories.CardRepository;
import com.processing.cardmanagement.repositories.ReservationRepository;
import com.processing.cardmanagement.repositories.ReservationRollbackRepository;
import com.processing.cardmanagement.services.retries.RetryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    private static final String PAN = "4000001234567890";
    private static final String BIN = "400000";
    private static final String RRN = "612345678901";
    private static final BigDecimal BALANCE = new BigDecimal("100000");
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final int MAX_PAGE_LIMIT = 100;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationRollbackRepository reservationRollbackRepository;

    @Mock
    private CardServiceDefaults defaults;

    @Mock
    private PanGenerator panGenerator;

    @Mock
    private CardEventNotifier eventNotifier;

    @Mock
    private BinIssuerService binIssuerService;

    private CardServiceImpl cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardServiceImpl(cardRepository, reservationRepository, reservationRollbackRepository,
                new FixedSettings(), defaults, panGenerator, eventNotifier, binIssuerService,
                new RetryServiceImpl(), new DirectTransactionRunner());
    }

    @Test
    void shouldDecreaseBalanceWhenReservationIsCreated() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.isUnique(RRN, PAN)).thenReturn(true);
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Card card = cardService.reserve(PAN, AMOUNT, RRN);

        // Assert
        assertThat(card.availableBalance()).isEqualByComparingTo(BALANCE.subtract(AMOUNT));
    }

    @Test
    void shouldPublishReserveEventWhenReservationIsCreated() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.isUnique(RRN, PAN)).thenReturn(true);
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        cardService.reserve(PAN, AMOUNT, RRN);

        // Assert
        verify(eventNotifier).notifyListeners(new CardServiceReserveEvent(PAN, RRN, AMOUNT));
    }

    @Test
    void shouldThrowCardNotFoundWhenReservingForUnknownPan() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> cardService.reserve(PAN, AMOUNT, RRN)).isInstanceOf(CardNotFoundException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldThrowReservationAlreadyExistsWhenRrnIsAlreadyReserved() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.isUnique(RRN, PAN)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> cardService.reserve(PAN, AMOUNT, RRN))
                .isInstanceOf(ReservationAlreadyExistsException.class);
        verify(cardRepository, never()).update(any());
    }

    @Test
    void shouldThrowInsufficientFundsWhenAmountExceedsBalance() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));

        // Act + Assert
        assertThatThrownBy(() -> cardService.reserve(PAN, BALANCE.add(BigDecimal.ONE), RRN))
                .isInstanceOf(InsufficientFundsException.class);
        verify(reservationRepository, never()).save(any());
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = CardStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldRejectReservationWhenCardIsNotActive(CardStatus status) {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(status)));

        // Act + Assert
        assertThatThrownBy(() -> cardService.reserve(PAN, AMOUNT, RRN)).isInstanceOf(IllegalStateException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldReturnFundsWhenReservationIsRolledBack() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(reservation(ReservationStatus.RESERVED)));
        when(reservationRollbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Card card = cardService.rollback(PAN, AMOUNT, RRN);

        // Assert
        assertThat(card.availableBalance()).isEqualByComparingTo(BALANCE.add(AMOUNT));
    }

    @Test
    void shouldSaveReservationAsRolledBackWhenRollbackSucceeds() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(reservation(ReservationStatus.RESERVED)));
        when(reservationRollbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);

        // Act
        cardService.rollback(PAN, AMOUNT, RRN);

        // Assert
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ReservationStatus.ROLLED_BACK);
    }

    @Test
    void shouldPublishRollbackEventWhenRollbackSucceeds() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(reservation(ReservationStatus.RESERVED)));
        when(reservationRollbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        cardService.rollback(PAN, AMOUNT, RRN);

        // Assert
        verify(eventNotifier).notifyListeners(new CardServiceRollbackEvent(PAN, RRN, AMOUNT));
    }

    @Test
    void shouldThrowReservationNotFoundWhenRollingBackUnknownRrn() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.findByRrnAndPanForUpdate(RRN, PAN)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> cardService.rollback(PAN, AMOUNT, RRN))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(cardRepository, never()).update(any());
    }

    @Test
    void shouldThrowRollbackAlreadySatisfiedWhenReservationIsRolledBackTwice() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        when(reservationRepository.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(reservation(ReservationStatus.ROLLED_BACK)));

        // Act + Assert
        assertThatThrownBy(() -> cardService.rollback(PAN, AMOUNT, RRN))
                .isInstanceOf(RollbackAlreadySatisfiedException.class);
        verify(cardRepository, never()).update(any());
    }

    @Test
    void shouldReturnCardWhenPanExists() {
        // Arrange
        Card card = card(CardStatus.ACTIVE);
        when(cardRepository.findByPan(PAN)).thenReturn(Optional.of(card));

        // Act
        Card found = cardService.getCard(PAN);

        // Assert
        assertThat(found).isEqualTo(card);
    }

    @Test
    void shouldThrowCardNotFoundWhenPanIsUnknown() {
        // Arrange
        when(cardRepository.findByPan(PAN)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> cardService.getCard(PAN)).isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void shouldUseDefaultPagingWhenLimitAndOffsetAreNotGiven() {
        // Arrange
        List<Card> page = List.of(card(CardStatus.ACTIVE));
        when(defaults.pageLimit()).thenReturn(20);
        when(defaults.pageOffset()).thenReturn(0L);
        when(cardRepository.findCards(20, 0L, null, null, null, null, null)).thenReturn(page);

        // Act
        List<Card> cards = cardService.getCards(null, null, null, null, null, null, null);

        // Assert
        assertThat(cards).isEqualTo(page);
    }

    @Test
    void shouldReturnCardsWhenLimitEqualsMaxPageLimit() {
        // Arrange
        List<Card> page = List.of(card(CardStatus.ACTIVE));
        when(cardRepository.findCards(MAX_PAGE_LIMIT, 10L, CardStatus.ACTIVE, BIN, null, null, null))
                .thenReturn(page);

        // Act
        List<Card> cards = cardService.getCards(MAX_PAGE_LIMIT, 10L, CardStatus.ACTIVE, BIN, null, null, null);

        // Assert
        assertThat(cards).isEqualTo(page);
    }

    @Test
    void shouldThrowTooLargeLimitWhenLimitExceedsMaxPageLimit() {
        // Act + Assert
        assertThatThrownBy(() -> cardService.getCards(MAX_PAGE_LIMIT + 1, null, null, null, null, null, null))
                .isInstanceOf(TooLargeLimitException.class);
    }

    @Test
    void shouldKeepCurrentValuesWhenPatchFieldsAreNotGiven() {
        // Arrange
        Card card = card(CardStatus.ACTIVE);
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card));
        when(cardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Card patched = cardService.patchCard(PAN, CardStatus.BLOCKED, null, null, null);

        // Assert
        assertThat(patched.status()).isEqualTo(CardStatus.BLOCKED);
        assertThat(patched.dailyLimit()).isEqualByComparingTo(card.dailyLimit());
        assertThat(patched.monthlyLimit()).isEqualByComparingTo(card.monthlyLimit());
        assertThat(patched.availableBalance()).isEqualByComparingTo(card.availableBalance());
    }

    @Test
    void shouldRejectPatchWhenMonthlyLimitBecomesLessThanDaily() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));

        // Act + Assert
        assertThatThrownBy(() -> cardService.patchCard(PAN, null, null, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).update(any());
    }

    @Test
    void shouldSaveCardAsDeletedWhenCardIsDeleted() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));
        ArgumentCaptor<Card> updated = ArgumentCaptor.forClass(Card.class);

        // Act
        cardService.deleteCard(PAN);

        // Assert
        verify(cardRepository).update(updated.capture());
        assertThat(updated.getValue().status()).isEqualTo(CardStatus.DELETED);
    }

    @Test
    void shouldPublishDeletionEventWhenCardIsDeleted() {
        // Arrange
        when(cardRepository.findByPanForUpdate(PAN)).thenReturn(Optional.of(card(CardStatus.ACTIVE)));

        // Act
        cardService.deleteCard(PAN);

        // Assert
        verify(eventNotifier).notifyListeners(new CardServiceDeletionEvent(PAN));
    }

    @ParameterizedTest(name = "bins={0}, pans={1}")
    @MethodSource("invalidBulkTargets")
    void shouldRejectBulkUpdateWhenNotExactlyOneTargetIsGiven(List<String> bins, List<String> pans) {
        // Act + Assert
        assertThatThrownBy(() -> cardService.bulkUpdateStatus(bins, pans, CardStatus.BLOCKED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnUpdatedCountWhenBulkUpdatingByBins() {
        // Arrange
        when(cardRepository.findCardsByBinsForUpdate(List.of(BIN)))
                .thenReturn(List.of(card(CardStatus.ACTIVE), card(CardStatus.INACTIVE)));

        // Act
        int updated = cardService.bulkUpdateStatus(List.of(BIN), null, CardStatus.BLOCKED);

        // Assert
        assertThat(updated).isEqualTo(2);
    }

    @Test
    void shouldSaveNewStatusWhenBulkUpdatingByPans() {
        // Arrange
        when(cardRepository.findCardsByPansForUpdate(List.of(PAN))).thenReturn(List.of(card(CardStatus.ACTIVE)));
        ArgumentCaptor<Card> updated = ArgumentCaptor.forClass(Card.class);

        // Act
        cardService.bulkUpdateStatus(null, List.of(PAN), CardStatus.BLOCKED);

        // Assert
        verify(cardRepository).update(updated.capture());
        assertThat(updated.getValue().status()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    void shouldCreateActiveCardWithGeneratedPanAndBinIssuerWhenCardIsCreated() {
        // Arrange
        when(binIssuerService.getIssuerId(BIN)).thenReturn("ISS001");
        when(panGenerator.generatePan(BIN)).thenReturn(PAN);
        when(cardRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Card card = cardService.createCard(BIN, "IVAN IVANOV", "643",
                new BigDecimal("1000"), new BigDecimal("30000"), BALANCE);

        // Assert
        assertThat(card.pan()).isEqualTo(PAN);
        assertThat(card.issuerId()).isEqualTo("ISS001");
        assertThat(card.status()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void shouldPublishCreationEventWhenCardIsCreated() {
        // Arrange
        when(binIssuerService.getIssuerId(BIN)).thenReturn("ISS001");
        when(panGenerator.generatePan(BIN)).thenReturn(PAN);

        // Act
        cardService.createCard(BIN, "IVAN IVANOV", "643", new BigDecimal("1000"), new BigDecimal("30000"), BALANCE);

        // Assert
        verify(eventNotifier).notifyListeners(new CardServiceCreationEvent(1));
    }

    @Test
    void shouldCreateCardsOneByOneWhenBatchInsertHasPanCollision() {
        // Arrange
        List<CardDraft> drafts = List.of(draft(), draft());
        when(binIssuerService.getIssuerId(BIN)).thenReturn("ISS001");
        when(panGenerator.generatePan(BIN)).thenReturn(PAN);
        when(cardRepository.createAll(anyList())).thenThrow(new MassiveCardCreationCollisionException());
        when(cardRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Card> created = cardService.createCards(drafts);

        // Assert
        assertThat(created).hasSize(2);
    }

    static Stream<Arguments> invalidBulkTargets() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(List.of(BIN), List.of(PAN)));
    }

    private static Card card(CardStatus status) {
        return new Card(UUID.fromString("00000000-0000-0000-0000-000000000001"), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2029, 10), status, "643", new BigDecimal("15000"), new BigDecimal("300000"),
                BALANCE, "ISS001", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Reservation reservation(ReservationStatus status) {
        Instant createdAt = Instant.parse("2026-10-21T10:15:30Z");
        return new Reservation(UUID.fromString("00000000-0000-0000-0000-000000000002"), PAN, AMOUNT, RRN, status,
                createdAt, createdAt);
    }

    private static CardDraft draft() {
        return new CardDraft(BIN, "IVAN IVANOV", CardStatus.ACTIVE, "643",
                new BigDecimal("1000"), new BigDecimal("30000"), BALANCE);
    }

    /** Fake: выполняет операцию сразу, без транзакции. */
    private static final class DirectTransactionRunner implements TransactionRunner {

        @Override
        public void run(Runnable operation) {
            operation.run();
        }

        @Override
        public <T> T runSupplier(Supplier<T> operation) {
            return operation.get();
        }

        @Override
        public <T> T runSupplierWithNestedQuery(Function<TransactionRunner, T> supplier) {
            return supplier.apply(this);
        }
    }

    private static final class FixedSettings implements CardServiceSettings {

        @Override
        public int cardValidityPeriod() {
            return 3;
        }

        @Override
        public int maxPageLimit() {
            return MAX_PAGE_LIMIT;
        }

        @Override
        public int maxCardCreationRetries() {
            return 2;
        }
    }
}
