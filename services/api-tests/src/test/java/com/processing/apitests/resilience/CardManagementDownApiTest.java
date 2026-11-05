package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import io.restassured.http.Method;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Отказ CMS: исход SERVICE_UNAVAILABLE, ответы 503 Gateway и /health. */
class CardManagementDownApiTest {

    private static final String SERVICE = "card-management";

    private static String pan;

    @BeforeAll
    static void restartCardManagement() {
        pan = Cards.active().pan();
        DockerStack.restart(SERVICE);
    }

    @AfterAll
    static void waitForCardManagement() {
        Recovery.restore(SERVICE);
    }

    /** tz/04 пункт 5 обещал код 05 и ISSUER_TIMEOUT. */
    @Test
    @KnownDiscrepancy("D-22")
    void shouldDeclineWithServiceUnavailableWhenCardCannotBeFetched() {
        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo("DECLINED"))
            .body("responseCode", equalTo("96"))
            .body("declineReason", equalTo("SERVICE_UNAVAILABLE"));
    }

    /** openapi.yaml, GET /health: 503 и status=degraded, если хотя бы одна зависимость недоступна; tz/02 пункт 1. */
    @Test
    void shouldReportDegradedStatusWhenCardManagementIsDown() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/health")
        .then()
            .statusCode(503)
            .body("status", equalTo("degraded"))
            .body("services.cardManagement", equalTo("unavailable"))
            .body("services.switch", equalTo("ok"));
    }

    /** Маршрут /api/cards/** помечен serviceName=cardManagement в application.yml Gateway: 503 обязан прийти на каждом. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api/cards/{pan}",
        "PATCH, /api/cards/{pan}",
        "DELETE, /api/cards/{pan}",
        "POST, /api/cards/{pan}/reserve",
        "POST, /api/cards/{pan}/rollback"
    })
    @KnownDiscrepancy("D-29")
    void shouldAnswerServiceUnavailableOnEveryCardRoute(Method method, String path) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of())
        .when()
            .request(method, path, pan)
        .then()
            .spec(ErrorBody.serviceUnavailable("cardManagement"));
    }

    @ParameterizedTest(name = "POST {0}")
    @ValueSource(strings = {"/api/cards", "/api/cards/generate", "/api/cards/bulk-update"})
    @KnownDiscrepancy("D-29")
    void shouldAnswerServiceUnavailableOnEveryCollectionRoute(String path) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of())
        .when()
            .post(path)
        .then()
            .spec(ErrorBody.serviceUnavailable("cardManagement"));
    }

    @Test
    @KnownDiscrepancy("D-36")
    void shouldAnswerSpringErrorOnDocsRouteWithoutServiceName() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/cards-docs")
        .then()
            .spec(ErrorBody.springDefault(500, "Internal Server Error", "/cards-docs"));
    }

    /** Терминал читает карты из CMS напрямую: GlobalExceptionHandler симулятора, ResourceAccessException -> 503. */
    @Test
    void shouldAnswer503FromTerminalSimulatorWhenCardsCannotBeRead() {
        given()
            .spec(ApiSpecs.terminalSimulator())
            .body(Map.of("count", 1, "tps", 1, "scenario", "normal"))
        .when()
            .post("/api/simulator/terminal/run")
        .then()
            .statusCode(503)
            .body("error", equalTo("Resource access"))
            .body("message", startsWith("I/O error on GET request for \"http://card-management:8080/api/cards\""))
            .body("serviceName", equalTo("terminal-simulator"));
    }

    /** Лекция 9: Retry-After сообщает, когда повторить. После 3 отказов Gateway размыкает цепь на 10 секунд. */
    @Test
    @KnownDiscrepancy({"D-17", "D-29"})
    void shouldOpenCircuitWithRetryAfterWhenFailuresReachThreshold() {
        IntStream.range(0, 3).forEach(attempt ->
                given().spec(ApiSpecs.gateway()).when().get("/api/cards").then().statusCode(503));

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards")
        .then()
            .statusCode(503)
            .header("Retry-After", "10")
            .body("error", equalTo("SERVICE_UNAVAILABLE"))
            .body("message", equalTo("cardManagement service is temporarily unavailable"))
            .body("serviceName", equalTo("cardManagement"))
            .body("$", not(hasKey("timestamp")));
    }
}
