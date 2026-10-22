package com.processing.service;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggerClientTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private LoggerClient loggerClient;

    @BeforeEach
    void setUp() {
        loggerClient = new LoggerClient(rabbitTemplate);
    }

    @Test
    void shouldPublishTransactionToTransactionsExchangeWhenBrokerIsAvailable() {
        // Arrange
        TransactionRequest transaction = transaction();

        // Act
        boolean published = loggerClient.log(transaction);

        // Assert
        assertThat(published).isTrue();
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);
    }

    @Test
    void shouldReportFailureWhenBrokerIsUnavailable() {
        // Arrange
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // Act
        boolean published = loggerClient.log(transaction());

        // Assert
        assertThat(published).isFalse();
    }

    private static TransactionRequest transaction() {
        return new TransactionRequest(
                UUID.randomUUID(), "0100", "000001", "612345678901", "4000001234567890", "000000",
                new BigDecimal("150000"), "643", "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001",
                "ISS001", null, TransactionStatus.APPROVED, null, "A1B2C3", 42,
                Instant.parse("2026-10-21T10:15:30Z"), Instant.parse("2026-10-21T10:15:31Z"));
    }
}
