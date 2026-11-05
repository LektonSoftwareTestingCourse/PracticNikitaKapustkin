package com.processing.apitests.routing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.KnownDiscrepancy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Третий источник перечня эндпоинтов - маршруты Gateway (application.yml). */
class RouteInventoryApiTest {

    /**
     * Внутренние вызовы недоступны извне: openapi.yaml. Остальные строки - расхождения:
     * merchants и WebSocket без маршрута (D-06), метод вне POST уходит в Logger (D-23),
     * объявленные /start и /status не реализованы и отвечают 500 (D-05). Поиск принимает только GET.
     */
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "POST, /api/internal/route, 404, path, /api/internal/route",
        "POST, /api/internal/authorize, 404, path, /api/internal/authorize",
        "POST, /api/internal/log, 404, path, /api/internal/log",
        "GET, /api/simulator/merchants, 404, path, /api/simulator/merchants",
        "GET, /ws/transactions, 404, path, /ws/transactions",
        "GET, /api/transactions, 404, serviceName, transaction-logger",
        "POST, /api/transactions/search, 405, error, Method not allowed",
        "GET, /api/simulator/terminal/status, 500, serviceName, terminal-simulator",
        "POST, /api/simulator/terminal/start, 500, serviceName, terminal-simulator"
    })
    @KnownDiscrepancy({"D-05", "D-06", "D-23"})
    void shouldAnswerByRouteTable(String method, String path, int status, String field, String value) {
        given()
            .spec(ApiSpecs.gateway())
            .body("{}")
        .when()
            .request(method, path)
        .then()
            .statusCode(status)
            .body(field, equalTo(value));
    }

    /** tz/02 пункт 7: Swagger UI на /docs. */
    @Test
    void shouldRedirectToSwaggerUi() {
        given()
            .spec(ApiSpecs.gateway())
            .redirects().follow(false)
        .when()
            .get("/docs")
        .then()
            .statusCode(302)
            .header("Location", "/webjars/swagger-ui/index.html");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/switch-docs", "/logger-docs", "/terminal-docs", "/merchant-docs", "/cards-docs"})
    void shouldServeOpenApiDocument(String path) {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get(path)
        .then()
            .statusCode(200)
            .body("openapi", notNullValue());
    }
}
