package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import java.time.Duration;

/** После восстановления сервиса проверяется не только /health: транзакция обязана снова одобряться. */
final class Recovery {

    private static final Duration LIMIT = Duration.ofSeconds(60);
    /** Consumer Logger переподключается к брокеру с шагом 5 секунд, замерено до 15 секунд и больше на старте брокера. */
    private static final Duration CONSUMER_LIMIT = Duration.ofSeconds(60);

    private Recovery() {
    }

    /** Брокер вернулся - ещё не значит, что вернулся потребитель: ждём, пока запись дойдёт до журнала. */
    static void restoreBroker() {
        restore("rabbitmq");
        String pan = Cards.active().pan();
        TransactionBody probe = TransactionBody.forPan(pan).amount(1);
        Transactions.approve(probe);
        Transactions.awaitStored(pan, probe.stan(), CONSUMER_LIMIT);
    }

    static void restore(String service) {
        DockerStack.restore(service);
        await().atMost(LIMIT).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
            .untilAsserted(() -> given()
                .spec(ApiSpecs.gateway())
                .body(TransactionBody.forPan(Cards.active().pan()).amount(1).asMap())
            .when()
                .post("/api/transactions")
            .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED")));
    }
}
