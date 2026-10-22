package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.CardEventNotifier;
import com.processing.cardmanagement.events.CardsBatchGeneratedEvent;
import com.processing.cardmanagement.exceptions.CardGenerationLimitException;
import com.processing.cardmanagement.models.CardDraft;
import com.processing.cardmanagement.models.CardStatus;
import com.processing.cardmanagement.options.CardGeneratorOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardGeneratorServiceTest {

    private static final int MAX_COUNT = 1000;
    private static final List<String> BINS = List.of("400000", "500000");

    @Mock
    private CardService cardService;

    @Mock
    private CardEventNotifier eventNotifier;

    private CardGeneratorService generator;

    @BeforeEach
    void setUp() {
        CardGeneratorOptions options = new CardGeneratorOptions(new BigDecimal("1000"), new BigDecimal("5000"),
                new BigDecimal("100"), new BigDecimal("500"), "643", MAX_COUNT);
        generator = new CardGeneratorService(cardService, options, eventNotifier);
    }

    @ParameterizedTest(name = "count {0} -> active {1}, inactive {2}, blocked {3}")
    @CsvSource({
            "100,  95,  3,  2",
            "1000, 950, 30, 20",
            "20,   19,  0,  1",
            "1,    0,   0,  1"
    })
    void shouldSplitStatusesByPercentagesWhenGeneratingCards(int count, int active, int inactive, int blocked) {
        // Arrange
        ArgumentCaptor<List<CardDraft>> drafts = draftsCaptor();
        when(cardService.createCards(drafts.capture())).thenReturn(List.of());

        // Act
        generator.generate(count, BINS);

        // Assert
        assertThat(drafts.getValue()).hasSize(count);
        assertThat(drafts.getValue()).filteredOn(draft -> draft.status() == CardStatus.ACTIVE).hasSize(active);
        assertThat(drafts.getValue()).filteredOn(draft -> draft.status() == CardStatus.INACTIVE).hasSize(inactive);
        assertThat(drafts.getValue()).filteredOn(draft -> draft.status() == CardStatus.BLOCKED).hasSize(blocked);
    }

    @Test
    void shouldAssignBinsRoundRobinWhenSeveralBinsAreGiven() {
        // Arrange
        ArgumentCaptor<List<CardDraft>> drafts = draftsCaptor();
        when(cardService.createCards(drafts.capture())).thenReturn(List.of());

        // Act
        generator.generate(4, BINS);

        // Assert
        assertThat(drafts.getValue()).extracting(CardDraft::bin)
                .containsExactly("400000", "500000", "400000", "500000");
    }

    @Test
    void shouldKeepLimitsAndBalanceWithinConfiguredRangesWhenGeneratingCards() {
        // Arrange
        ArgumentCaptor<List<CardDraft>> drafts = draftsCaptor();
        when(cardService.createCards(drafts.capture())).thenReturn(List.of());

        // Act
        generator.generate(50, BINS);

        // Assert
        assertThat(drafts.getValue()).allSatisfy(draft -> {
            assertThat(draft.initialBalance()).isBetween(new BigDecimal("1000"), new BigDecimal("4999"));
            assertThat(draft.dailyLimit()).isBetween(new BigDecimal("100"), new BigDecimal("499"));
            assertThat(draft.monthlyLimit()).isEqualByComparingTo(draft.dailyLimit().multiply(BigDecimal.valueOf(30)));
            assertThat(draft.currencyCode()).isEqualTo("643");
        });
    }

    @Test
    void shouldPublishStatusCountsWhenBatchIsGenerated() {
        // Arrange
        when(cardService.createCards(anyList())).thenReturn(List.of());

        // Act
        generator.generate(100, BINS);

        // Assert
        verify(eventNotifier).notifyListeners(new CardsBatchGeneratedEvent(Map.of(
                CardStatus.ACTIVE, 95L, CardStatus.INACTIVE, 3L, CardStatus.BLOCKED, 2L)));
    }

    @Test
    void shouldThrowGenerationLimitWhenCountExceedsMaxCount() {
        // Act + Assert
        assertThatThrownBy(() -> generator.generate(MAX_COUNT + 1, BINS))
                .isInstanceOf(CardGenerationLimitException.class);
        verify(cardService, never()).createCards(any());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<CardDraft>> draftsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
