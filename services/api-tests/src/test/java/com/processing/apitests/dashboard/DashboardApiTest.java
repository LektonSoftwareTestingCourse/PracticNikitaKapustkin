package com.processing.apitests.dashboard;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.LogRecord;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Источник: /api/dashboard/* в api-spec.md и openapi.yaml, DashboardController, ChartsFilter. */
class DashboardApiTest {

    /** Статистика глобальная, поэтому проверяется прирост, а не абсолютное значение. */
    @Test
    void shouldCountNewTransactionInStats() {
        int total = given().spec(ApiSpecs.gateway())
                .when().get("/api/dashboard/stats")
                .then().statusCode(200).extract().path("totalTransactions");
        Transactions.approve(TransactionBody.forPan(Cards.active().pan()));

        await().atMost(Transactions.ASYNC_LIMIT).pollDelay(Transactions.FIRST_CHECK).pollInterval(Transactions.POLL_INTERVAL)
            .untilAsserted(() -> given()
                .spec(ApiSpecs.gateway())
            .when()
                .get("/api/dashboard/stats")
            .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/dashboard-stats.json"))
                .body("totalTransactions", greaterThanOrEqualTo(total + 1))
                .body("transactionsPerMinute", greaterThanOrEqualTo(1)));
    }

    /** tz/08: сортировка createdAt DESC. */
    @Test
    void shouldPutNewestTransactionFirst() {
        String pan = Cards.active().pan();
        TransactionBody transaction = TransactionBody.forPan(pan);
        Transactions.approve(transaction);
        Transactions.awaitStored(pan, transaction.stan());

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("limit", 1)
        .when()
            .get("/api/dashboard/recent")
        .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].stan", equalTo(transaction.stan()));
    }

    /** openapi.yaml: maximum 100, код принимает до 500. */
    @ParameterizedTest(name = "limit={0} -> {1}")
    @MethodSource("recentLimits")
    @KnownDiscrepancy("D-20")
    void shouldValidateLimit(int limit, int status, Matcher<?> body) {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam("limit", limit)
        .when()
            .get("/api/dashboard/recent")
        .then()
            .statusCode(status)
            .body("", body);
    }

    static Stream<Arguments> recentLimits() {
        return Stream.of(
                Arguments.of(1, 200, hasSize(lessThanOrEqualTo(1))),
                Arguments.of(101, 200, hasSize(lessThanOrEqualTo(101))),
                Arguments.of(500, 200, hasSize(lessThanOrEqualTo(500))),
                Arguments.of(0, 400, hasEntry("message", "getRecent.limit: limit must be positive")),
                Arguments.of(501, 400, hasEntry("message", "getRecent.limit: limit must not exceed 500")));
    }

    /** Группировка по createdAt. Час теста случаен и в прошлом, поэтому в корзине только его записи. */
    @ParameterizedTest(name = "granularity={0}")
    @CsvSource({"hour, :00:00Z, 13", "day, T00:00:00Z, 10"})
    void shouldAggregateRecordsIntoBucket(String granularity, String bucketEnd, int prefix) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String hour = String.format("20%02d-%02d-%02dT%02d", random.nextInt(20), random.nextInt(1, 13),
                random.nextInt(1, 29), random.nextInt(24));
        String pan = TestData.nonExistentPan();
        Transactions.log(LogRecord.approved(pan).with("createdAt", hour + ":10:00Z").with("amount", 100));
        Transactions.log(LogRecord.declined(pan, "CARD_BLOCKED").with("createdAt", hour + ":20:00Z").with("amount", 50));

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("granularity", granularity)
            .queryParam("from", hour + ":00:00Z")
            .queryParam("to", hour + ":59:59Z")
        .when()
            .get("/api/dashboard/charts")
        .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].timestamp", equalTo(hour.substring(0, prefix) + bucketEnd))
            .body("[0].total", equalTo(2))
            .body("[0].approved", equalTo(1))
            .body("[0].declined", equalTo(1))
            .body("[0].amount", equalTo(150));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "granularity, week, 'granularity: granularity must be hour or day'",
        "from, bad, 'from: Failed to convert'",
        "to, bad, 'to: Failed to convert'"
    })
    void shouldAnswer400WhenChartParameterIsInvalid(String parameter, String value, String message) {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam(parameter, value)
        .when()
            .get("/api/dashboard/charts")
        .then()
            .spec(ErrorBody.logger(400, "Validation error"))
            .body("message", startsWith(message));
    }
}
