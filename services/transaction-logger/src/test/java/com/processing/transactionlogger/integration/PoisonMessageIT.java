package com.processing.transactionlogger.integration;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class PoisonMessageIT {
    private static final LoggerEnvironment ENV = new LoggerEnvironment();
    @Container
    static final GenericContainer<?> LOGGER = ENV.logger;

    @BeforeEach
    void prepareQueuesAndDatabase() throws Exception {
        try (Connection connection = ENV.connect(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare("smp.transactions.dlx", "direct", true);
            channel.queueDeclare("transaction-log-dlq", true, false, false, Map.of("x-message-ttl", 60_000));
            channel.queueBind("transaction-log-dlq", "smp.transactions.dlx", "transaction-log");
            ENV.purge(channel);
        }
        ENV.database().execute("TRUNCATE transactions RESTART IDENTITY CASCADE");
    }

    @Test
    void rejectsMalformedMessageAndStoresNextTransaction() throws Exception {
        var random = ThreadLocalRandom.current();
        String stan = "%06d".formatted(random.nextInt(1_000_000));
        String rrn = "%012d".formatted(random.nextLong(1_000_000_000_000L));
        String pan = "400000%010d".formatted(random.nextLong(10_000_000_000L));
        byte[] poison = ("{\"stan\":\"" + stan + "\"").getBytes(StandardCharsets.UTF_8);
        try (Connection connection = ENV.connect(); Channel channel = connection.createChannel()) {
            assertThat(ENV.rabbit.execInContainer("rabbitmqctl", "trace_on").getExitCode()).isZero();
            String deliveries = channel.queueDeclare().getQueue();
            channel.queueBind(deliveries, "amq.rabbitmq.trace", "deliver.transaction-log");
            publish(channel, "application/json", poison);
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                var dead = channel.basicGet("transaction-log-dlq", true);
                assertThat(dead).isNotNull();
                assertThat(dead.getBody()).isEqualTo(poison);
                assertThat(dead.getProps().getHeaders().get("x-first-death-reason").toString()).isEqualTo("rejected");
            });
            var delivery = channel.basicGet(deliveries, true);
            assertThat(delivery).isNotNull();
            assertThat(delivery.getBody()).isEqualTo(poison);
            assertThat(channel.basicGet(deliveries, true)).isNull();
            assertThat(channel.messageCount("transaction-log")).isZero();

            var control = new TransactionRequest(UUID.randomUUID(), "0100", stan, rrn, pan, "000000", BigDecimal.TEN,
                    "643", "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", "ISS001", BigDecimal.ONE,
                    TransactionStatus.APPROVED, null, "123456", 10, Instant.now(), Instant.now());
            var message = new SimpleMessageConverter().toMessage(control, new MessageProperties());
            publish(channel, message.getMessageProperties().getContentType(), message.getBody());
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                    assertThat(ENV.database().queryForObject(
                            "SELECT count(*) FROM transactions WHERE id = ? AND rrn = ? AND stan = ?",
                            Integer.class, control.id(), rrn, stan)).isOne());
        }
    }

    private static void publish(Channel channel, String type, byte[] body) throws Exception {
        channel.basicPublish("smp.transactions", "transaction.log", new AMQP.BasicProperties.Builder()
                .contentType(type).build(), body);
    }

    @AfterAll
    static void closeEnvironment() {
        LOGGER.stop();
        ENV.rabbit.stop();
        ENV.postgres.stop();
        ENV.network.close();
    }
}
