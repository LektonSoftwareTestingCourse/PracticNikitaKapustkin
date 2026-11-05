package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Источник: tz/03 пункт 4 и лекция 10: ни одна транзакция не остаётся APPROVED без записи в очереди.
 * При недоступном брокере одобрение откатывается, клиент получает 96.
 */
class BrokerDownApiTest {

    private static final String SERVICE = "rabbitmq";

    private String pan;

    @BeforeEach
    void stopBroker() {
        pan = Cards.withBalance(10_000).pan();
        DockerStack.stop(SERVICE);
    }

    @AfterEach
    void startBroker() {
        Recovery.restoreBroker();
    }

    @Test
    @KnownDiscrepancy("D-01")
    void shouldDeclineWithSystemErrorAndReturnFundsWhenPublishFails() {
        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(1_000).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("mti", equalTo("0110"))
            .body("status", equalTo("DECLINED"))
            .body("responseCode", equalTo("96"))
            .body("declineReason", equalTo("System error"))
            .body("rrn", nullValue())
            .body("authCode", nullValue());

        assertThat(Cards.balanceOf(pan), equalTo(10_000L));
    }

    /**
     * Отсутствие эффекта отката. После запуска брокера идёт контрольная транзакция той же карты:
     * её появление значит, что очередь дочитана, и только потом условие держится окно Transactions.ABSENCE_WINDOW.
     */
    @Test
    void shouldNotStoreRolledBackTransactionWhenBrokerReturns() {
        TransactionBody rolledBack = TransactionBody.forPan(pan).amount(100);
        Transactions.submit(rolledBack);
        Recovery.restoreBroker();

        TransactionBody control = TransactionBody.forPan(pan).amount(100);
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
                .body("transactions.findAll { it.stan == '" + rolledBack.stan() + "' }", hasSize(0)));
    }
}
