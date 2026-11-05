package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Источник: openapi.yaml, 503 у поиска; маршруты logger и dashboard помечены serviceName=logger в application.yml Gateway. */
class LoggerDownApiTest {

    private static final String SERVICE = "transaction-logger";

    @BeforeAll
    static void restartLogger() {
        DockerStack.restart(SERVICE);
    }

    @AfterAll
    static void waitForLogger() {
        Recovery.restore(SERVICE);
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/transactions/search",
        "/api/transactions/export",
        "/api/dashboard/stats",
        "/api/dashboard/recent",
        "/api/dashboard/charts"
    })
    @KnownDiscrepancy("D-29")
    void shouldAnswerServiceUnavailableOnEveryLoggerRoute(String path) {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get(path)
        .then()
            .spec(ErrorBody.serviceUnavailable("logger"));
    }

    @Test
    @KnownDiscrepancy("D-36")
    void shouldAnswerSpringErrorOnDocsRouteWithoutServiceName() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/logger-docs")
        .then()
            .spec(ErrorBody.springDefault(500, "Internal Server Error", "/logger-docs"));
    }
}
