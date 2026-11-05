package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.emptyString;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import io.restassured.http.Method;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Маршрут /api/simulator/terminal/** помечен serviceName=terminalSimulator в application.yml Gateway.
 * Симуляторы перезапускаются по одному: при одновременном рестарте контейнеры меняются адресами (D-37).
 */
class TerminalSimulatorDownApiTest {

    private static final String SERVICE = "terminal-simulator";
    /** Старт симулятора до 10 секунд, затем до 10 секунд открытой цепи. */
    private static final Duration RECOVERY_LIMIT = Duration.ofSeconds(60);

    @BeforeAll
    static void restartTerminalSimulator() {
        DockerStack.restart(SERVICE);
    }

    /** Симулятор вне цепочки авторизации: восстановление проверяется его маршрутом через Gateway. */
    @AfterAll
    static void waitForTerminalSimulator() {
        await().atMost(RECOVERY_LIMIT).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
            .untilAsserted(() -> given()
                .spec(ApiSpecs.gateway())
            .when()
                .post("/api/simulator/terminal/stop")
            .then()
                .statusCode(200)
                .body(emptyString()));
    }

    /** start и status в коде отсутствуют (D-05), но попадают в маршрут и тоже отвечают 503. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "POST, /api/simulator/terminal/run",
        "POST, /api/simulator/terminal/start-continuous",
        "POST, /api/simulator/terminal/stop",
        "POST, /api/simulator/terminal/start",
        "GET, /api/simulator/terminal/status"
    })
    @KnownDiscrepancy("D-29")
    void shouldAnswerServiceUnavailableOnEveryTerminalRoute(Method method, String path) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of())
        .when()
            .request(method, path)
        .then()
            .spec(ErrorBody.serviceUnavailable("terminalSimulator"));
    }

    @Test
    @KnownDiscrepancy("D-36")
    void shouldAnswerSpringErrorOnDocsRouteWithoutServiceName() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/terminal-docs")
        .then()
            .spec(ErrorBody.springDefault(500, "Internal Server Error", "/terminal-docs"));
    }
}
