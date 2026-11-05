package com.processing.apitests.internal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.LogRecord;
import com.processing.apitests.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Повторная доставка: лекция 10, три исхода - новая запись, существующая запись, конфликт данных.
 * Порт 8088: маршрута через Gateway нет. Запись считается GPath-фильтром по id: фильтра stan в поиске нет.
 */
class LoggerIdempotencyApiTest {

    private String pan;

    @BeforeEach
    void useOwnPan() {
        pan = TestData.nonExistentPan();
    }

    /** openapi.yaml обещает LogResponse со status=already_exists, tz/08 - существующую запись: код следует ТЗ (D-19). */
    @Test
    @KnownDiscrepancy("D-19")
    void shouldKeepOneRecordWhenSameMessageIsRedelivered() {
        LogRecord record = LogRecord.approved(pan);

        given()
            .spec(ApiSpecs.logger())
            .body(record.asMap())
        .when()
            .post("/api/internal/log")
        .then()
            .statusCode(201)
            .body("id", equalTo(record.id()))
            .body("status", equalTo("stored"));

        given()
            .spec(ApiSpecs.logger())
            .body(record.asMap())
        .when()
            .post("/api/internal/log")
        .then()
            .statusCode(200)
            .body("id", equalTo(record.id()))
            .body("status", equalTo("APPROVED"));

        assertSingleRecord(record);
    }

    /** Ключ совпал, данные разошлись. createdAt в сравнении не участвует: TransactionMapper.matches. */
    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource({
        "amount, 150001, 409, message, Transaction with id {id} already exists with different data",
        "merchantId, MERCH0000000099, 409, message, Transaction with id {id} already exists with different data",
        "status, DECLINED, 409, message, Transaction with id {id} already exists with different data",
        "createdAt, 2026-06-02T00:00:00Z, 200, status, APPROVED"
    })
    void shouldCompareDataWhenKeyMatches(String field, String value, int status, String path, String expected) {
        LogRecord record = LogRecord.approved(pan);
        given().spec(ApiSpecs.logger()).body(record.asMap())
                .when().post("/api/internal/log")
                .then().statusCode(201);

        given()
            .spec(ApiSpecs.logger())
            .body(record.copy().with(field, value).asMap())
        .when()
            .post("/api/internal/log")
        .then()
            .statusCode(status)
            .body(path, equalTo(expected.replace("{id}", record.id())));

        assertSingleRecord(record);
    }

    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
        "id, NULL, Validation error, id: must not be null",
        "amount, 0, Validation error, amount: must be greater than 0",
        "stan, 12, Validation error, stan: stan must be exactly 6 digits",
        "pan, 123, Validation error, pan: PAN number must contain exactly 16 digits",
        "acquirerId, ACQ000000001, Validation error, acquirerId: size must be between 0 and 10",
        "status, UNKNOWN, Invalid request body, malformed"
    })
    void shouldNameRuleWhenRecordIsInvalid(String field, String value, String error, String message) {
        given()
            .spec(ApiSpecs.logger())
            .body(LogRecord.approved(pan).with(field, value).asMap())
        .when()
            .post("/api/internal/log")
        .then()
            .spec(ErrorBody.logger(400, error))
            .body("message", containsString(message));
    }

    private void assertSingleRecord(LogRecord record) {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
        .when()
            .get("/api/transactions/search")
        .then()
            .statusCode(200)
            .body("transactions.findAll { it.id == '" + record.id() + "' }.size()", equalTo(1))
            .body("transactions.find { it.id == '" + record.id() + "' }.amount", equalTo(150_000));
    }
}
