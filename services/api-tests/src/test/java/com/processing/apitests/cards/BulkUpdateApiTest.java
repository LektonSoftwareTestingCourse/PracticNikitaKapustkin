package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Эндпоинт есть только в коде (CardController.bulkUpdate), но открыт через Gateway маршрутом /api/cards/**. */
class BulkUpdateApiTest {

    @Test
    void shouldUpdateStatusOfListedCards() {
        String pan = Cards.active().pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of("pans", List.of(pan), "status", "BLOCKED"))
        .when()
            .post("/api/cards/bulk-update")
        .then()
            .statusCode(200)
            .body("updated", equalTo(1));
    }

    /** OWN - карта теста. Передавать нужно ровно одно из полей bins и pans. Сообщение о PAN не называет поле (D-17). */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "{\"status\":\"BLOCKED\"} | IllegalArgumentException | Either bin or pans must be provided",
        "{\"pans\":[\"OWN\"],\"bins\":[\"400000\"],\"status\":\"BLOCKED\"} | IllegalArgumentException "
            + "| Only one of bin or pans must be provided",
        "{\"pans\":[\"12\"],\"status\":\"BLOCKED\"} | MethodArgumentNotValidException "
            + "| PAN number must contain exactly 16 digits"
    })
    @KnownDiscrepancy({"D-17", "D-21"})
    void shouldRejectUpdateWhenTargetIsAmbiguous(String body, String error, String message) {
        given()
            .spec(ApiSpecs.gateway())
            .body(body.replace("OWN", Cards.active().pan()))
        .when()
            .post("/api/cards/bulk-update")
        .then()
            .spec(ErrorBody.cardManagement(400, error, message));
    }
}
