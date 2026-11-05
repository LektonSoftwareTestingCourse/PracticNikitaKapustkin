package com.processing.apitests.simulator;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.Transactions;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Источник: POST /api/simulator/merchant/run в api-spec.md и openapi.yaml, SimulationController. */
class MerchantSimulatorApiTest {

    @BeforeAll
    static void prepareActiveCards() {
        Cards.active();
    }

    @ParameterizedTest(name = "scenario={0}")
    @ValueSource(strings = {"grocery", "electronics", "restaurant", "travel"})
    void shouldProcessRequestedCountWhenScenarioIsDeclared(String scenario) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of("count", 2, "scenario", scenario))
        .when()
            .post("/api/simulator/merchant/run")
        .then()
            .statusCode(200)
            .body("totalSubmitted", equalTo(2))
            .body("transactions", hasSize(2));
    }

    /**
     * Switch получает через /fee комиссию для журнала, Gateway открывает его маршрутом /api/simulator/merchant/**.
     * В спецификации эндпоинта нет (D-21). Запрос строится из записи журнала, в которую Switch положил комиссию.
     */
    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("feeRequests")
    @KnownDiscrepancy("D-21")
    void shouldReturnFeeOfSimulatedTransaction(String request, Supplier<Map<String, Object>> body, int status,
                                               String field, Matcher<?> value) {
        given()
            .spec(ApiSpecs.gateway())
            .body(body.get())
        .when()
            .post("/api/simulator/merchant/fee")
        .then()
            .statusCode(status)
            .body(field, value);
    }

    static Stream<Arguments> feeRequests() {
        Supplier<Map<String, Object>> simulated = () -> {
            given().spec(ApiSpecs.gateway()).body(Map.of("count", 1, "scenario", "grocery"))
                    .when().post("/api/simulator/merchant/run").then().statusCode(200);
            JsonPath record = awaitNewestRecordWithFee();
            return Map.of("transmissionDateTime", record.getString("transmissionDateTime"),
                    "pan", record.getString("pan"), "stan", record.getString("stan"),
                    "amount", record.getLong("amount"), "terminalId", record.getString("terminalId"));
        };
        Supplier<Map<String, Object>> unknown = () -> Map.of("transmissionDateTime", "2026-06-01T10:30:00Z",
                "pan", TestData.nonExistentPan(), "stan", TestData.uniqueStan(), "terminalId", "TERM0001");
        Supplier<Map<String, Object>> empty = Map::of;
        return Stream.of(
                Arguments.of("транзакция симулятора", simulated, 200, "acquirerFee", notNullValue()),
                Arguments.of("неизвестная транзакция", unknown, 404, "error", equalTo("Resource not found")),
                Arguments.of("пустой запрос", empty, 400, "error", equalTo("Invalid request")));
    }

    private static JsonPath awaitNewestRecordWithFee() {
        await().atMost(Transactions.ASYNC_LIMIT).pollDelay(Transactions.FIRST_CHECK).pollInterval(Transactions.POLL_INTERVAL)
            .untilAsserted(() -> given().spec(ApiSpecs.gateway()).queryParam("limit", 20)
                .when().get("/api/dashboard/recent")
                .then().statusCode(200)
                .body("findAll { it.acquiringFee != null }.size()", greaterThanOrEqualTo(1)));
        return given().spec(ApiSpecs.gateway()).queryParam("limit", 20)
                .when().get("/api/dashboard/recent")
                .then().extract().jsonPath().setRootPath("find { it.acquiringFee != null }");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "{\"count\":0,\"scenario\":\"grocery\"} | 400 | count : must be greater than or equal to 1",
        "{\"count\":1} | 400 | scenario : Scenario is required",
        "{\"count\":1,\"scenario\":\"nope\"} | 400 | nope",
        "{\"count\":1,\"scenario\":\"grocery\",\"mccCodes\":[\"0000\"]} | 404 | Merchants with given mcc ([0000]) not found"
    })
    void shouldRejectRun(String body, int status, String messagePart) {
        given()
            .spec(ApiSpecs.gateway())
            .body(body)
        .when()
            .post("/api/simulator/merchant/run")
        .then()
            .statusCode(status)
            .body("message", containsString(messagePart))
            .body("serviceName", equalTo("Merchant acquirer simulator"));
    }
}
