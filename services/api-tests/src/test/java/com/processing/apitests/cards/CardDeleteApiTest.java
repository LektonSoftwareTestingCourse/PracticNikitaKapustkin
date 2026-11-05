package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.TestData;
import org.junit.jupiter.api.Test;

/** Источник: DELETE /api/cards/{pan} в openapi.yaml: мягкое удаление, удалённая карта не возвращается. */
class CardDeleteApiTest {

    @Test
    void shouldHideCardWhenItIsDeleted() {
        String pan = Cards.active().pan();
        String notFound = "Card with PAN 4000********" + TestData.last4(pan) + " was not found";

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .delete("/api/cards/{pan}", pan)
        .then()
            .statusCode(204)
            .body(emptyString());

        given()
            .spec(ApiSpecs.cardManagement())
        .when()
            .get("/api/cards/{pan}", pan)
        .then()
            .spec(ErrorBody.cardManagement(404, "CardNotFoundException", notFound));

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .delete("/api/cards/{pan}", pan)
        .then()
            .spec(ErrorBody.cardManagement(404, "CardNotFoundException", notFound));
    }

    @Test
    void shouldAnswer400WhenPanIsMalformed() {
        given()
            .spec(ApiSpecs.gateway())
        .when()
            .delete("/api/cards/{pan}", "123")
        .then()
            .spec(ErrorBody.cardManagement(400, "ConstraintViolationException",
                    "deleteCard.pan: PAN number must contain exactly 16 digits"));
    }
}
