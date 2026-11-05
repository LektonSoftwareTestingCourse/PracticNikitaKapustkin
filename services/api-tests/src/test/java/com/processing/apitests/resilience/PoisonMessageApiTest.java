package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * acquirerId длиннее колонки varchar(10): Gateway и Authorization его пропускают, Logger не может сохранить.
 * docs/architecture.md: после исчерпания попыток сообщение уходит в DLQ. Фактически x-max-delivery
 * не поддерживается RabbitMQ, сообщение возвращается в очередь бесконечно. Очередь чистится после теста.
 */
@KnownDiscrepancy({"D-16", "D-14"})
class PoisonMessageApiTest {

    private static final String LOGGER = "transaction-logger";
    private static final String QUEUE = "transaction-log";
    private static final String DLQ = "transaction-log-dlq";
    /** Статистика Management API обновляется раз в 5 секунд, запас на медленный раннер. */
    private static final Duration MANAGEMENT_STATS_REFRESH = Duration.ofSeconds(20);

    @AfterEach
    void removePoisonMessage() {
        DockerStack.stop(LOGGER);
        rabbit().when().delete("/api/queues/%2F/" + QUEUE + "/contents").then().statusCode(204);
        Recovery.restore(LOGGER);
    }

    @Test
    void shouldLoseApprovedTransactionAndKeepMessageInQueueWhenLoggerRejectsIt() {
        String pan = Cards.withBalance(1_000).pan();
        TransactionBody poison = TransactionBody.forPan(pan).amount(100).with("acquirerId", "ACQ000000001");

        given()
            .spec(ApiSpecs.gateway())
            .body(poison.asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"));

        TransactionBody control = TransactionBody.forPan(pan).amount(100);
        Transactions.approve(control);
        Transactions.awaitStored(pan, control.stan());

        Transactions.assertTotalStored(pan, 1);
        assertThat("денег списано за две транзакции", Cards.balanceOf(pan), equalTo(800L));
        await().atMost(MANAGEMENT_STATS_REFRESH).pollInterval(Transactions.POLL_INTERVAL)
            .untilAsserted(() -> assertThat(messagesIn(QUEUE), greaterThanOrEqualTo(1)));
        assertThat(messagesIn(DLQ), equalTo(0));
    }

    private static int messagesIn(String queue) {
        return rabbit().when().get("/api/queues/%2F/" + queue + "?columns=messages")
                .then().statusCode(200).extract().path("messages");
    }

    private static RequestSpecification rabbit() {
        return given().spec(ApiSpecs.rabbitManagement());
    }
}
