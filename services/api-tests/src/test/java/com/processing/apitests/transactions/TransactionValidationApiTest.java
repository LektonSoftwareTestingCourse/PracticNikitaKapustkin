package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.TransactionBody;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Правила TransactionRequestValidator и TransactionValidationFilter: один невалидный класс на запрос.
 * Отказ выносит Gateway: это видно по serviceName, и карта для такого запроса не нужна.
 */
class TransactionValidationApiTest {

    private static final String INVALID_JSON = "Request body must be valid JSON";

    @ParameterizedTest(name = "without {0}")
    @ValueSource(strings = {"mti", "stan", "pan", "processingCode", "currencyCode", "transmissionDateTime",
        "terminalId", "merchantId", "mcc", "acquirerId", "amount"})
    void shouldRejectTransactionWhenRequiredFieldIsAbsent(String field) {
        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(TestData.nonExistentPan()).without(field).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .spec(ErrorBody.gatewayValidation("Field '" + field + "' is required"));
    }

    @ParameterizedTest(name = "{0}=''{1}''")
    @CsvSource(nullValues = "NULL", value = {
        "pan, NULL, Field 'pan' is required",
        "pan, '   ', Field 'pan' is required",
        "pan, 400000123456789, Field 'pan' must be exactly 16 digits",
        "pan, 40000012345678901, Field 'pan' must be exactly 16 digits",
        "pan, 400000123456789A, Field 'pan' must be exactly 16 digits",
        "mti, 0200, Field 'mti' must be '0100'",
        "mti, 100, Field 'mti' must be '0100'",
        "amount, 0, Field 'amount' must be > 0",
        "amount, -1, Field 'amount' must be > 0",
        "amount, abc, " + INVALID_JSON,
        "currencyCode, 64, Field 'currencyCode' must be exactly 3 characters",
        "currencyCode, 6430, Field 'currencyCode' must be exactly 3 characters",
        "mcc, 541, Field 'mcc' must be exactly 4 digits",
        "mcc, 54110, Field 'mcc' must be exactly 4 digits",
        "mcc, 54A1, Field 'mcc' must be exactly 4 digits",
        "transmissionDateTime, 2026-06-01T10:30:00, " + INVALID_JSON,
        "transmissionDateTime, 2026-13-01T10:30:00Z, " + INVALID_JSON
    })
    void shouldRejectTransactionWhenFieldBreaksRule(String field, String value, String message) {
        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(TestData.nonExistentPan()).with(field, value).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .spec(ErrorBody.gatewayValidation(message));
    }

    @ParameterizedTest(name = "body={0}")
    @CsvSource(delimiter = '|', value = {
        "'{bad' | " + INVALID_JSON,
        "'[]' | " + INVALID_JSON,
        "'null' | Request body is required"
    })
    void shouldRejectBodyWhenItIsNotJsonObject(String body, String message) {
        given()
            .spec(ApiSpecs.gateway())
            .body(body)
        .when()
            .post("/api/transactions")
        .then()
            .spec(ErrorBody.gatewayValidation(message));
    }

    /** Значения вне контрактного формата Gateway пропускает. Короткий stan становится отказом 05 после 3 попыток. */
    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
        "stan, ABC123, APPROVED, 00, NULL",
        "currencyCode, ABC, APPROVED, 00, NULL",
        "processingCode, ABCDEF, APPROVED, 00, NULL",
        "terminalType, KIOSK, APPROVED, 00, NULL",
        "amount, 0.5, APPROVED, 00, NULL",
        "stan, 12, DECLINED, 05, Authorization service unavailable"
    })
    @KnownDiscrepancy({"D-14", "D-15"})
    void shouldNotRejectValuesThatBreakDeclaredFormat(String field, String value, String status, String code,
                                                      String reason) {
        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(Cards.active().pan()).with(field, value).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo(status))
            .body("responseCode", equalTo(code))
            .body("declineReason", equalTo(reason));
    }

    /** Формат тела Gateway читает при любом Content-Type, тип отклоняет уже Switch: отказ другого сервиса. Тело - формат Spring (D-17). */
    @Test
    @KnownDiscrepancy("D-17")
    void shouldAnswer415FromSwitchWhenContentTypeIsNotJson() throws JsonProcessingException {
        given()
            .spec(ApiSpecs.gateway())
            .contentType(ContentType.TEXT)
            .body(new ObjectMapper().writeValueAsString(TransactionBody.forPan(TestData.nonExistentPan()).asMap()))
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(415)
            .body("error", equalTo("Unsupported Media Type"))
            .body("path", equalTo("/api/internal/route"));
    }
}
