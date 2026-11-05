package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import org.junit.jupiter.api.Test;

/**
 * Сквозная цепочка: Gateway - Switch - Authorization - RabbitMQ - Logger - поиск.
 * Эффект асинхронный, поэтому ждём условие. Источник: лекция 10, раздел «Асинхронные контракты».
 */
class EndToEndApiTest {

    /** Значения из примера api-spec.md не лезут в формат: Switch дополняет terminalId и обрезает merchantId (D-26). */
    @Test
    @KnownDiscrepancy("D-26")
    void shouldStoreApprovedTransactionWhenItPassesWholeChain() {
        String pan = Cards.active().pan();
        TransactionBody transaction = TransactionBody.forPan(pan).amount(150_000)
                .with("terminalId", "TERM001").with("merchantId", "MERCH12345678901");
        String rrn = Transactions.approve(transaction);

        Transactions.awaitStored(pan, transaction.stan());

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body(matchesJsonSchemaInClasspath("schemas/search-response.json"))
            .body("total", equalTo(1))
            .body("transactions[0].rrn", equalTo(rrn))
            .body("transactions[0].amount", equalTo(150_000))
            .body("transactions[0].issuerId", equalTo(TestData.KNOWN_ISSUER))
            .body("transactions[0].terminalId", equalTo("TERM0010"))
            .body("transactions[0].merchantId", equalTo("MERCH1234567890"))
            .body("transactions[0].acquiringFee", nullValue());
    }

    /** openapi.yaml: Transaction включает AuthorizationResponse, но responseCode Logger не хранит (D-38). */
    @Test
    @KnownDiscrepancy("D-38")
    void shouldStoreDeclinedTransactionWithReasonAndWithoutRrn() {
        String pan = Cards.withStatus("BLOCKED").pan();
        TransactionBody transaction = TransactionBody.forPan(pan);
        Transactions.submit(transaction);

        Transactions.awaitStored(pan, transaction.stan());

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("transactions[0].status", equalTo("DECLINED"))
            .body("transactions[0].declineReason", equalTo("CARD_BLOCKED"))
            .body("transactions[0]", not(hasKey("responseCode")))
            .body("transactions[0].rrn", nullValue());
    }

    /**
     * Проверка отсутствия. Пустой поиск ничего не доказывает, если очередь ещё не дочитана, поэтому следом идёт
     * контрольная транзакция той же карты: очередь FIFO, появилась контрольная - значит, всё раньше обработано.
     * После этого условие держится ещё 2 секунды: за это время пришла бы запоздавшая запись.
     */
    @Test
    void shouldNotStoreTransactionWhenGatewayRejectedIt() {
        String pan = Cards.active().pan();
        TransactionBody rejected = TransactionBody.forPan(pan).amount(0);
        given().spec(ApiSpecs.gateway()).body(rejected.asMap())
                .when().post("/api/transactions")
                .then().statusCode(400);

        TransactionBody control = TransactionBody.forPan(pan);
        Transactions.approve(control);
        Transactions.awaitStored(pan, control.stan());

        await().during(Transactions.ABSENCE_WINDOW).atMost(Transactions.ABSENCE_LIMIT)
            .pollInterval(Transactions.POLL_INTERVAL)
            .untilAsserted(() -> given()
                .spec(ApiSpecs.gateway())
                .queryParam("pan", pan)
            .when()
                .get("/api/transactions/search")
            .then()
                .statusCode(200)
                .body("transactions.findAll { it.stan == '" + rejected.stan() + "' }", hasSize(0)));
    }
}
