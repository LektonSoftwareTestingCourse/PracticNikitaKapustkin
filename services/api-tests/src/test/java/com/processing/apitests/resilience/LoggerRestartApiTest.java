package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import org.junit.jupiter.api.Test;

/** Источник: лекция 10: очередь хранит сообщение, пока потребителя нет. */
class LoggerRestartApiTest {

    private static final String SERVICE = "transaction-logger";

    /** Запись асинхронная: отказ Logger не мешает авторизации, durable-очередь доставит сообщение после запуска. */
    @Test
    void shouldKeepApprovingAndDeliverRecordWhenLoggerRestarts() {
        String pan = Cards.active().pan();
        TransactionBody transaction = TransactionBody.forPan(pan);
        DockerStack.restart(SERVICE);

        given()
            .spec(ApiSpecs.gateway())
            .body(transaction.asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"));

        Recovery.restore(SERVICE);
        Transactions.awaitStored(pan, transaction.stan());
    }
}
