package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import org.junit.jupiter.api.Test;

/** Источник: tz/03 пункт 4 (retry и decline 05), лекция 10 (паузы 1 и 2 секунды между тремя попытками). */
class AuthorizationDownApiTest {

    private static final String SERVICE = "authorization";

    /** openapi.yaml: 503 с serviceName=authorization. Фактически 200 и decline по ТЗ Switch. */
    @Test
    @KnownDiscrepancy("D-22")
    void shouldDeclineAfterRetriesWhenAuthorizationIsDown() {
        TransactionBody transaction = TransactionBody.forPan(Cards.active().pan());
        DockerStack.restart(SERVICE);

        given()
            .spec(ApiSpecs.gateway())
            .body(transaction.asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .time(greaterThanOrEqualTo(3_000L))
            .body("status", equalTo("DECLINED"))
            .body("responseCode", equalTo("05"))
            .body("declineReason", equalTo("Authorization service unavailable"))
            .body("rrn", nullValue());

        Recovery.restore(SERVICE);
    }
}
