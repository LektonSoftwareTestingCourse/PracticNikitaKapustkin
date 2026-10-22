package com.processing.cardmanagement.services;

import com.processing.cardmanagement.config.RabbitMQConfig;
import com.processing.cardmanagement.events.CardServiceDeletionEvent;
import com.processing.cardmanagement.models.CardOutboxEventData;
import com.processing.cardmanagement.models.OutboxEventDataStatus;
import com.processing.cardmanagement.options.OutboxOptions;
import com.processing.cardmanagement.repositories.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorImplTest {

    private static final int MAX_RETRY_COUNT = 3;
    private static final CardServiceDeletionEvent EVENT = new CardServiceDeletionEvent("4000001234567890");

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxEventProcessorImpl processor;

    @BeforeEach
    void setUp() {
        processor = new OutboxEventProcessorImpl(outboxRepository, rabbitTemplate,
                new OutboxOptions(1000, MAX_RETRY_COUNT));
    }

    @Test
    void shouldPublishWithEventTypeRoutingKeyWhenProcessingEvent() {
        // Act
        processor.processSingleEvent(pendingEvent(0));

        // Assert
        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.CARD_EVENTS_EXCHANGE, "card.CardServiceDeletionEvent", EVENT);
    }

    @Test
    void shouldSaveEventAsProcessedWhenPublishSucceeds() {
        // Arrange
        ArgumentCaptor<CardOutboxEventData> saved = ArgumentCaptor.forClass(CardOutboxEventData.class);

        // Act
        processor.processSingleEvent(pendingEvent(0));

        // Assert
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(OutboxEventDataStatus.PROCESSED);
        assertThat(saved.getValue().processedAt()).isNotNull();
    }

    @ParameterizedTest(name = "retries before {0} -> {1}")
    @CsvSource({
            "0, PENDING",
            "1, PENDING",
            "2, FAILED",
            "5, FAILED"
    })
    void shouldCountRetryAndFailEventWhenRetriesAreExhausted(int retriesBefore, OutboxEventDataStatus expected) {
        // Arrange
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        ArgumentCaptor<CardOutboxEventData> saved = ArgumentCaptor.forClass(CardOutboxEventData.class);

        // Act
        processor.processSingleEvent(pendingEvent(retriesBefore));

        // Assert
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().retryCount()).isEqualTo(retriesBefore + 1);
        assertThat(saved.getValue().lastError()).contains("Connection refused");
        assertThat(saved.getValue().status()).isEqualTo(expected);
    }

    private static CardOutboxEventData pendingEvent(int retryCount) {
        return new CardOutboxEventData(UUID.randomUUID(), EVENT, Instant.parse("2026-10-21T10:15:30Z"),
                null, retryCount, null, OutboxEventDataStatus.PENDING);
    }
}
