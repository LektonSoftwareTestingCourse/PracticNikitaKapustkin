package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Источник: GET /api/cards/{pan} в openapi.yaml. Полного PAN в тексте ошибки быть не должно. */
class CardGetApiTest {

    @Test
    void shouldReturnCardWhenPanExists() {
        String pan = Cards.withBalance(5_000).pan();

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .body("pan", equalTo(pan))
            .body("availableBalance", equalTo(5_000));
    }

    /** NONE - уникальный PAN без карты; в ответе он маскируется до последних четырёх цифр LAST4. */
    @ParameterizedTest(name = "pan={0} -> {1}")
    @CsvSource({
        "NONE, 404, CardNotFoundException, Card with PAN 4000********LAST4 was not found",
        "123, 400, ConstraintViolationException, getCard.pan: PAN number must contain exactly 16 digits",
        "400000123456789A, 400, ConstraintViolationException, getCard.pan: PAN number must contain exactly 16 digits"
    })
    void shouldAnswerErrorWhenCardCannotBeFound(String pan, int status, String error, String message) {
        String target = pan.replace("NONE", TestData.nonExistentPan());

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards/{pan}", target)
        .then()
            .spec(ErrorBody.cardManagement(status, error, message.replace("LAST4", TestData.last4(target))));
    }
}
