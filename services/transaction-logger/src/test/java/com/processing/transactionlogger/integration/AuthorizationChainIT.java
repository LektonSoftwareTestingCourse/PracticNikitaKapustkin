package com.processing.transactionlogger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class AuthorizationChainIT {
    private static final LoggerEnvironment ENV = new LoggerEnvironment();
    private static final GenericContainer<?> STUBS = new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withNetwork(ENV.network).withNetworkAliases("stubs").withExposedPorts(8080)
            .withCopyFileToContainer(MountableFile.forClasspathResource("practice-6/mappings"),
                    "/home/wiremock/mappings")
            .waitingFor(Wait.forHttp("/__admin/mappings"));
    private static final GenericContainer<?> CARDS = ENV.service("card-management");
    private static final GenericContainer<?> AUTH = ENV.service("authorization")
            .withEnv("CARD_MGMT_URL", "http://card-management:8080")
            .withEnv("BIN_LOOKUP_URL", "http://stubs:8080").dependsOn(CARDS, STUBS);
    @Container
    static final GenericContainer<?> SWITCH = ENV.service("switch")
            .withEnv("AUTH_URL", "http://authorization:8080")
            .withEnv("LOGGER_URL", "http://transaction-logger:8080")
            .withEnv("MERCHANT_URL", "http://stubs:8080").dependsOn(AUTH, ENV.logger);

    @BeforeEach
    void clearTestData() throws Exception {
        ENV.database().execute("TRUNCATE transactions, limit_usage, reservations, reservation_rollbacks, "
                + "cards, outbox_event RESTART IDENTITY CASCADE");
        try (Connection connection = ENV.connect(); Channel channel = connection.createChannel()) {
            ENV.purge(channel);
        }
    }

    @Test
    void approvedPaymentReservesFundsUpdatesUsageAndReachesJournal() {
        JsonNode card = client(CARDS).post().uri("/api/cards").body(Map.of(
                "bin", "400000", "cardholderName", "IVAN IVANOV", "currencyCode", "643",
                "dailyLimit", 1000, "monthlyLimit", 5000, "initialBalance", 2000))
                .retrieve().body(JsonNode.class);
        String pan = card.required("pan").asText();
        String stan = "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
        var request = Map.ofEntries(Map.entry("mti", "0100"), Map.entry("pan", pan), Map.entry("stan", stan),
                Map.entry("processingCode", "000000"), Map.entry("amount", 125), Map.entry("currencyCode", "643"),
                Map.entry("transmissionDateTime", "2026-01-20T12:00:00Z"), Map.entry("terminalType", "POS"),
                Map.entry("terminalId", "TERM0001"), Map.entry("merchantId", "MERCH0000000001"),
                Map.entry("mcc", "5411"), Map.entry("acquirerId", "ACQ001"));
        JsonNode response = client(SWITCH).post().uri("/api/internal/route").body(request).retrieve().body(JsonNode.class);
        assertThat(response.required("status").asText()).isEqualTo("APPROVED");
        assertThat(response.required("stan").asText()).isEqualTo(stan);
        String rrn = response.required("rrn").asText();
        assertThat(rrn).matches("[0-9]{12}");

        var db = ENV.database();
        assertThat(db.queryForObject("""
                SELECT count(*) FROM reservations WHERE pan = ? AND rrn = ?
                AND reservation_amount = 125 AND status = 'RESERVED'
                """, Integer.class, pan, rrn)).isOne();
        assertThat(db.queryForObject("""
                SELECT count(*) FROM limit_usage WHERE pan = ? AND usage_date = DATE '2026-01-20'
                AND daily_amount = 125 AND monthly_amount = 125
                """, Integer.class, pan)).isOne();
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(db.queryForObject("""
                        SELECT count(*) FROM transactions WHERE rrn = ? AND stan = ? AND pan = ?
                        AND status = 'APPROVED' AND amount = 125 AND acquiring_fee = 7
                        """, Integer.class, rrn, stan, pan)).isOne());
        JsonNode lookups = client(STUBS).post().uri("/__admin/requests/count")
                .body(Map.of("url", "/api/bin/400000", "method", "GET")).retrieve().body(JsonNode.class);
        assertThat(lookups.required("count").asInt()).isOne();
    }

    private static RestClient client(GenericContainer<?> container) {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl("http://" + container.getHost() + ":" + container.getMappedPort(8080)).build();
    }

    @AfterAll
    static void closeEnvironment() {
        SWITCH.stop();
        AUTH.stop();
        CARDS.stop();
        STUBS.stop();
        ENV.logger.stop();
        ENV.rabbit.stop();
        ENV.postgres.stop();
        ENV.network.close();
    }
}
