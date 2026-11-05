package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.LogRecord;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.Transactions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Источник: GET /api/transactions/search в openapi.yaml, TransactionFilter, TransactionSpecification.
 * Записи создаются синхронно через POST /api/internal/log под уникальный PAN, он же изолирует выборку.
 */
class TransactionSearchApiTest {

    private String pan;

    @BeforeEach
    void useOwnPan() {
        pan = TestData.nonExistentPan();
    }

    /** Пустая выборка - это 200 и total=0, не 404: лекция 10. */
    @Test
    void shouldAnswer200WithZeroTotalWhenNothingMatches() {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("total", equalTo(0))
            .body("transactions", empty());
    }

    /** Попарное покрытие: модель pairwise/search-filters.model, строки сгенерированы PICT. issuerId и mcc есть только в openapi.yaml. */
    @ParameterizedTest(name = "{0} {1} {2} {3} {4} {5} -> {6}")
    @KnownDiscrepancy("D-02")
    @CsvFileSource(resources = "/pairwise/search-filters.csv", numLinesToSkip = 1)
    void shouldCombineFiltersWithAnd(String status, String merchantId, String mcc, String issuerId,
                                     String dateFrom, String dateTo, String expected) {
        Map<String, LogRecord> records = seedFourRecords();
        Map<String, String> filters = new HashMap<>(Map.of("status", status, "merchantId", merchantId, "mcc", mcc,
                "issuerId", issuerId, "dateFrom", dateFrom, "dateTo", dateTo));
        filters.values().removeIf("ANY"::equals);
        List<String> ids = Arrays.stream(expected.split(" "))
                .filter(records::containsKey).map(key -> records.get(key).id()).toList();

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
            .queryParams(filters)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("total", equalTo(ids.size()))
            .body("transactions.id", containsInAnyOrder(ids.toArray()));
    }

    /** openapi.yaml: `_` и `-` нормализуются, регистр игнорируется, совпадение по словам, а не LIKE. */
    @ParameterizedTest(name = "declineReason=''{0}'' -> {1}")
    @CsvSource({"CARD_NOT_FOUND, 1", "card not found, 1", "card-not-found, 1", "found, 1", "fou, 0"})
    void shouldMatchDeclineReasonAsFullText(String query, int total) {
        Transactions.log(LogRecord.declined(pan, "CARD_NOT_FOUND"));
        Transactions.log(LogRecord.declined(pan, "INSUFFICIENT_FUNDS"));

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
            .queryParam("declineReason", query)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("total", equalTo(total));
    }

    /** TransactionSpecification: dateFrom и dateTo - сутки UTC включительно, сравнивается transmissionDateTime. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "2026-03-10T23:59:59Z, 0",
        "2026-03-11T00:00:00Z, 1",
        "2026-03-12T23:59:59Z, 1",
        "2026-03-13T00:00:00Z, 0"
    })
    void shouldIncludeWholeDaysOfDateRange(String transmittedAt, int total) {
        Transactions.log(LogRecord.approved(pan).with("transmissionDateTime", transmittedAt));

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
            .queryParam("dateFrom", "2026-03-11")
            .queryParam("dateTo", "2026-03-12")
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("total", equalTo(total));
    }

    /** Границы limit из openapi.yaml, сортировка createdAt DESC из OffsetBasedPageRequest, total не зависит от страницы. */
    @ParameterizedTest(name = "limit={0} offset={1} -> {2}")
    @CsvSource({"1, 0, 1", "500, 0, 3", "2, 1, 2", "2, 3, 0"})
    void shouldPageRecordsNewestFirst(int limit, int offset, int size) {
        List<LogRecord> newestFirst = List.of(
                LogRecord.approved(pan).with("createdAt", "2026-06-01T12:00:00Z"),
                LogRecord.approved(pan).with("createdAt", "2026-06-01T11:00:00Z"),
                LogRecord.approved(pan).with("createdAt", "2026-06-01T10:00:00Z"));
        newestFirst.forEach(Transactions::log);

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
            .queryParam("limit", limit)
            .queryParam("offset", offset)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("total", equalTo(3))
            .body("transactions.id", equalTo(newestFirst.stream().skip(offset).limit(size)
                    .map(LogRecord::id).toList()));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "limit, 0, limit: must be greater than 0",
        "limit, 501, limit: limit must not exceed 500",
        "offset, -1, offset: must be greater than or equal to 0",
        "status, NOPE, 'status: Failed to convert'",
        "dateFrom, bad, 'dateFrom: Failed to convert'"
    })
    void shouldNameParameterWhenItIsInvalid(String parameter, String value, String message) {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam(parameter, value)
        .when()
            .get("/api/transactions/search")
        .then()
            .spec(ErrorBody.logger(400, "Validation error"))
            .body("message", containsString(message));
    }

    /** Некорректная комбинация отклоняется, но message пустой: ни поля, ни правила не названо. */
    @Test
    @KnownDiscrepancy("D-25")
    void shouldAnswer400WithEmptyMessageWhenDateRangeIsInverted() {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam("dateFrom", "2026-02-02")
            .queryParam("dateTo", "2026-01-01")
        .when()
            .get("/api/transactions/search")
        .then()
            .spec(ErrorBody.logger(400, "Validation error"))
            .body("message", emptyString());
    }

    private Map<String, LogRecord> seedFourRecords() {
        Map<String, LogRecord> records = Map.of(
                "r1", LogRecord.approved(pan).with("merchantId", "MERCHA000000001").with("mcc", "5411")
                        .with("issuerId", "ISS001").with("transmissionDateTime", "2026-03-10T12:00:00Z"),
                "r2", LogRecord.declined(pan, "CARD_BLOCKED").with("merchantId", "MERCHA000000001").with("mcc", "5812")
                        .with("issuerId", "ISS002").with("transmissionDateTime", "2026-03-11T12:00:00Z"),
                "r3", LogRecord.approved(pan).with("merchantId", "MERCHB000000001").with("mcc", "5812")
                        .with("issuerId", "ISS001").with("transmissionDateTime", "2026-03-12T12:00:00Z"),
                "r4", LogRecord.declined(pan, "CARD_BLOCKED").with("merchantId", "MERCHB000000001").with("mcc", "5411")
                        .with("issuerId", "ISS002").with("transmissionDateTime", "2026-03-13T12:00:00Z"));
        records.values().forEach(Transactions::log);
        return records;
    }
}
