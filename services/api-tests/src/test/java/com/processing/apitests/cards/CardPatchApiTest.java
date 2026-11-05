package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Источник: PATCH /api/cards/{pan} и CardUpdateRequest в openapi.yaml, Card.withData. */
class CardPatchApiTest {

    /** Частичное обновление: остальные поля не меняются. EXPIRED контрактом не объявлен (D-28). */
    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "BLOCKED", "EXPIRED"})
    @KnownDiscrepancy("D-28")
    void shouldChangeOnlyPassedField(String status) {
        String pan = Cards.active().pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of("status", status))
        .when()
            .patch("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .body("status", equalTo(status))
            .body("dailyLimit", equalTo((int) Cards.DAILY_LIMIT))
            .body("availableBalance", equalTo((int) Cards.BALANCE));
    }

    /** OWN - карта теста, NONE - уникальный PAN без карты. Запрос напрямую в CMS: через Gateway 500 засчитался бы circuit breaker. */
    @ParameterizedTest(name = "{0} {1}={2} -> {3}")
    @CsvSource({
        "OWN, monthlyLimit, 1, 400, IllegalArgumentException, Daily limit can not be larger than monthly limit",
        "OWN, dailyLimit, -1, 400, MethodArgumentNotValidException, Value can not be negative",
        "NONE, status, BLOCKED, 404, CardNotFoundException, Card with PAN 4000********{last4} was not found",
        "123, status, BLOCKED, 400, ConstraintViolationException, 'patchCard.pan: PAN number must contain exactly 16 digits'",
        "OWN, status, NOPE, 500, HttpMessageNotReadableException, 'JSON parse error: Cannot deserialize value of type'"
    })
    @KnownDiscrepancy({"D-07", "D-17"})
    void shouldRejectChange(String pan, String field, String value, int status, String error, String message) {
        String target = pan.replace("OWN", Cards.active().pan()).replace("NONE", TestData.nonExistentPan());

        given()
            .spec(ApiSpecs.cardManagement())
            .body(Map.of(field, value))
        .when()
            .patch("/api/cards/{pan}", target)
        .then()
            .spec(ErrorBody.cardManagement(status, error))
            .body("message", startsWith(message.replace("{last4}", TestData.last4(target))));
    }

    /** Отрицательный баланс PATCH принимает, хотя для лимитов отрицательные значения запрещены (D-27). */
    @Test
    @KnownDiscrepancy("D-27")
    void shouldAcceptNegativeBalance() {
        String pan = Cards.active().pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of("availableBalance", -1))
        .when()
            .patch("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .body("availableBalance", equalTo(-1));
    }
}
