package com.processing.apitests.simulator;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Источник: POST /api/simulator/terminal/run в api-spec.md, TerminalRunRequest. */
class TerminalSimulatorApiTest {

    /** Сценарии берут карты из пула: ACTIVE и BLOCKED должны существовать, CardRegistry. */
    @BeforeAll
    static void prepareCardPool() {
        Cards.active();
        Cards.withStatus("BLOCKED");
    }

    @ParameterizedTest(name = "scenario={0}")
    @ValueSource(strings = {"normal", "mixed", "high_value", "night_time", "declines_test"})
    void shouldProcessRequestedCountWhenScenarioIsDeclared(String scenario) {
        given()
            .spec(ApiSpecs.gateway())
            .body("{\"count\":3,\"scenario\":\"" + scenario + "\",\"tps\":50}")
        .when()
            .post("/api/simulator/terminal/run")
        .then()
            .statusCode(200)
            .body("totalSubmitted", equalTo(3))
            .body("transactions", hasSize(3));
    }

    /** Непрерывный режим есть только в коде и открыт через Gateway (D-21). Повторный запуск - IllegalStateException, 422. */
    @Test
    @KnownDiscrepancy("D-21")
    void shouldRunContinuousSimulationOnlyOnceAndStopIt() {
        given()
            .spec(ApiSpecs.gateway())
            .body("{\"tps\":1,\"transactionType\":\"NORMAL\"}")
        .when()
            .post("/api/simulator/terminal/start-continuous")
        .then()
            .statusCode(200)
            .body(emptyString());

        given()
            .spec(ApiSpecs.gateway())
            .body("{\"tps\":1,\"transactionType\":\"NORMAL\"}")
        .when()
            .post("/api/simulator/terminal/start-continuous")
        .then()
            .statusCode(422)
            .body("message", equalTo("Continuous simulation is already running!"));

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .post("/api/simulator/terminal/stop")
        .then()
            .statusCode(200)
            .body(emptyString());
    }

    /** Первая строка - пример из api-spec.md: код требует ещё tps (D-04). */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "{\"count\":1,\"scenario\":\"normal\"} | Validation failed | tps",
        "{\"count\":0,\"scenario\":\"normal\",\"tps\":1} | Validation failed | count",
        "{\"count\":1,\"scenario\":\"nope\",\"tps\":1} | Invalid request format | nope"
    })
    @KnownDiscrepancy("D-04")
    void shouldRejectRun(String body, String error, String messagePart) {
        given()
            .spec(ApiSpecs.gateway())
            .body(body)
        .when()
            .post("/api/simulator/terminal/run")
        .then()
            .statusCode(400)
            .body("error", equalTo(error))
            .body("message", containsString(messagePart))
            .body("serviceName", equalTo("terminal-simulator"));
    }
}
