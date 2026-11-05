package com.processing.apitests.resilience;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.DockerStack;
import com.processing.apitests.support.KnownDiscrepancy;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Мерчант берёт карты и проводит транзакции через Gateway: GatewayClient, ExternalServiceException -> 502. */
class GatewayDownApiTest {

    private static final String SERVICE = "gateway";

    @BeforeAll
    static void restartGateway() {
        DockerStack.restart(SERVICE);
    }

    @AfterAll
    static void waitForGateway() {
        Recovery.restore(SERVICE);
    }

    /** serviceName называет Card management, хотя недоступен Gateway (D-17). */
    @Test
    @KnownDiscrepancy("D-17")
    void shouldAnswer502FromMerchantSimulatorWhenGatewayIsUnreachable() {
        given()
            .spec(ApiSpecs.merchantSimulator())
            .body(Map.of("count", 1, "scenario", "grocery"))
        .when()
            .post("/api/simulator/merchant/run")
        .then()
            .statusCode(502)
            .body("error", equalTo("External service error"))
            .body("message", startsWith("I/O error on GET request for \"http://gateway:8080/api/cards\""))
            .body("serviceName", equalTo("Card management"));
    }
}
