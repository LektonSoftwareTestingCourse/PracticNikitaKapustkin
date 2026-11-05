package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Маршрут /api/simulator/merchant/** помечен serviceName=merchantSimulator в application.yml Gateway. */
class MerchantSimulatorDownApiTest {

    private static final String SERVICE = "merchant-acquirer";
    /** Старт симулятора до 10 секунд, затем до 10 секунд открытой цепи. */
    private static final Duration RECOVERY_LIMIT = Duration.ofSeconds(60);

    @BeforeAll
    static void restartMerchantSimulator() {
        DockerStack.restart(SERVICE);
    }

    /** Пустое тело отклоняет сам симулятор: значит, Gateway снова до него доходит. */
    @AfterAll
    static void waitForMerchantSimulator() {
        await().atMost(RECOVERY_LIMIT).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
            .untilAsserted(() -> given()
                .spec(ApiSpecs.gateway())
                .body(Map.of())
            .when()
                .post("/api/simulator/merchant/fee")
            .then()
                .statusCode(400)
                .body("serviceName", equalTo("Merchant acquirer simulator")));
    }

    @ParameterizedTest(name = "POST {0}")
    @ValueSource(strings = {"/api/simulator/merchant/run", "/api/simulator/merchant/fee"})
    @KnownDiscrepancy("D-29")
    void shouldAnswerServiceUnavailableOnEveryMerchantRoute(String path) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of())
        .when()
            .post(path)
        .then()
            .spec(ErrorBody.serviceUnavailable("merchantSimulator"));
    }

    @Test
    @KnownDiscrepancy("D-36")
    void shouldAnswerSpringErrorOnDocsRouteWithoutServiceName() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/merchant-docs")
        .then()
            .spec(ErrorBody.springDefault(500, "Internal Server Error", "/merchant-docs"));
    }
}
