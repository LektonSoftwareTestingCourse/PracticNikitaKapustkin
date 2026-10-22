package com.processing.cardmanagement.events;

import com.processing.cardmanagement.models.CardOutboxEventData;
import com.processing.cardmanagement.services.OutboxEventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CardEventNotifierImplTest {

    @Mock
    private OutboxEventProcessor outboxEventProcessor;

    @Mock
    private CardEventListener listener;

    private CardEventNotifierImpl notifier;

    @BeforeEach
    void setUp() {
        notifier = new CardEventNotifierImpl(outboxEventProcessor, List.of(listener));
    }

    @Test
    void shouldStoreEventInOutboxWhenEventIsOutboxEvent() {
        // Arrange
        CardServiceDeletionEvent event = new CardServiceDeletionEvent("4000001234567890");
        ArgumentCaptor<CardOutboxEventData> saved = ArgumentCaptor.forClass(CardOutboxEventData.class);

        // Act
        notifier.notifyListeners(event);

        // Assert
        verify(outboxEventProcessor).save(saved.capture());
        assertThat(saved.getValue().event()).isEqualTo(event);
        verify(listener, never()).onEvent(any());
    }

    @Test
    void shouldPassEventToListenersWhenEventIsNotOutboxEvent() {
        // Arrange
        CardsAmountUpdatedEvent event = new CardsAmountUpdatedEvent(42);

        // Act
        notifier.notifyListeners(event);

        // Assert
        verify(listener).onEvent(event);
        verify(outboxEventProcessor, never()).save(any());
    }
}
