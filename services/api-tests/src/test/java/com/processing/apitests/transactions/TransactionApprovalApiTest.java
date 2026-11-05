package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import io.restassured.http.ContentType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Источник: POST /api/transactions в api-spec.md, лекция 9: транспорт, бизнес-результат, генерируемые значения.
 * Границы класса «хватает средств»: минимальная сумма и сумма, равная балансу.
 */
class TransactionApprovalApiTest {

    /** api-spec.md: mti ответа 0110, фактически 0100 (D-01). */
    @ParameterizedTest(name = "amount={0}, balance=1000")
    @ValueSource(longs = {1, 1000})
    @KnownDiscrepancy("D-01")
    void shouldApproveAndReserveAmountWhenItDoesNotExceedBalance(long amount) {
        String pan = Cards.withBalance(1_000).pan();
        TransactionBody transaction = TransactionBody.forPan(pan).amount(amount);

        given()
            .spec(ApiSpecs.gateway())
            .body(transaction.asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/authorization-response.json"))
            .body("mti", equalTo("0100"))
            .body("stan", equalTo(transaction.stan()))
            .body("status", equalTo("APPROVED"))
            .body("responseCode", equalTo("00"))
            .body("rrn", matchesPattern("\\d{12}"))
            .body("authCode", matchesPattern("[A-Z0-9]{6}"))
            .body("processingTimeMs", greaterThanOrEqualTo(0))
            .body("$", hasKey("declineReason"))
            .body("declineReason", nullValue());

        assertThat(Cards.balanceOf(pan), equalTo(1_000 - amount));
    }
}
