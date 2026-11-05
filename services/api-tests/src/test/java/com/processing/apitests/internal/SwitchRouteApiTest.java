package com.processing.apitests.internal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import org.junit.jupiter.api.Test;

/** Источник: POST /api/internal/route в api-spec.md, RouteController. Вызов напрямую на порт Switch. */
class SwitchRouteApiTest {

    @Test
    void shouldApproveWhenRoutedDirectly() {
        TransactionBody transaction = TransactionBody.forPan(Cards.active().pan());

        given()
            .spec(ApiSpecs.switchService())
            .body(transaction.asMap())
        .when()
            .post("/api/internal/route")
        .then()
            .statusCode(200)
            .body("stan", equalTo(transaction.stan()))
            .body("status", equalTo("APPROVED"));
    }

    /** Контракт описывает ErrorResponse, обработчика в Switch нет: ответ Spring по умолчанию. */
    @Test
    @KnownDiscrepancy("D-17")
    void shouldAnswerSpringDefaultBodyWhenJsonIsMalformed() {
        given()
            .spec(ApiSpecs.switchService())
            .body("{bad")
        .when()
            .post("/api/internal/route")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("path", equalTo("/api/internal/route"));
    }
}
