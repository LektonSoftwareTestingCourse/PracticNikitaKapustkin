package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Источник: openapi.yaml, 503 у POST /api/transactions; tz/02 пункт 5. */
class SwitchDownApiTest {

    private static final String SERVICE = "switch";

    private static TransactionBody transaction;

    @BeforeAll
    static void restartSwitch() {
        transaction = TransactionBody.forPan(Cards.active().pan());
        DockerStack.restart(SERVICE);
    }

    @AfterAll
    static void waitForSwitch() {
        Recovery.restore(SERVICE);
    }

    @Test
    void shouldAnswer503NamingSwitchWhenTransactionCannotBeRouted() {
        given()
            .spec(ApiSpecs.gateway())
            .body(transaction.asMap())
        .when()
            .post("/api/transactions")
        .then()
            .spec(ErrorBody.serviceUnavailable("switch"));
    }

    @Test
    @KnownDiscrepancy("D-36")
    void shouldAnswerSpringErrorOnDocsRouteWithoutServiceName() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/switch-docs")
        .then()
            .spec(ErrorBody.springDefault(500, "Internal Server Error", "/switch-docs"));
    }
}
