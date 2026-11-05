package com.processing.apitests.health;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Источник: GET /health в docs/api-spec.md. Ответ 503 проверяется в CardManagementDownApiTest. */
class HealthApiTest {

    @Test
    void shouldReportEveryDownstreamOkWhenStackIsUp() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/health")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/gateway-health.json"))
            .body("status", equalTo("ok"))
            .body("service", equalTo("gateway"))
            .body("version", equalTo("1.0.0"))
            .body("services.switch", equalTo("ok"))
            .body("services.authorization", equalTo("ok"))
            .body("services.cardManagement", equalTo("ok"))
            .body("services.logger", equalTo("ok"));
    }
}
