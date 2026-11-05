package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Источник: POST /api/cards/{pan}/rollback в openapi.yaml, Reservation.startRollback. */
class RollbackCardApiTest {

    @Test
    void shouldReturnFundsOnlyOnceWhenRollbackIsRepeated() {
        String pan = Cards.withBalance(1_000).pan();
        String rrn = TestData.uniqueRrn();
        Cards.reserve(pan, 300, rrn);

        given()
            .spec(ApiSpecs.gateway())
            .body(rollback(pan, 300, rrn))
        .when()
            .post("/api/cards/{pan}/rollback", pan)
        .then()
            .statusCode(200)
            .body("availableBalance", equalTo(1_000));

        given()
            .spec(ApiSpecs.gateway())
            .body(rollback(pan, 300, rrn))
        .when()
            .post("/api/cards/{pan}/rollback", pan)
        .then()
            .spec(ErrorBody.cardManagement(409, "RollbackAlreadySatisfiedException",
                "Rollback with RRN \"" + rrn + "\" is already satisfied"));

        assertThat(Cards.balanceOf(pan), equalTo(1_000L));
    }

    /** OWN - карта теста, NONE - уникальный PAN без карты, RRN - уникальный номер без резерва. */
    @ParameterizedTest(name = "pan={0} amount={1} rrn={2} -> {3}")
    @CsvSource({
        "OWN, 100, RRN, 404, ReservationNotFoundException, "
            + "'Can not find reservation with RRN \"{rrn}\" and PAN \"4000********{last4}\"'",
        "NONE, 100, RRN, 404, CardNotFoundException, Card with PAN 4000********{last4} was not found",
        "OWN, 0, RRN, 400, MethodArgumentNotValidException, must be greater than 0",
        "OWN, 100, 12, 400, MethodArgumentNotValidException, RRN number must contain exactly 12 digits"
    })
    @KnownDiscrepancy("D-17")
    void shouldRejectRollback(String pan, long amount, String rrn, int status, String error, String message) {
        String target = pan.replace("OWN", Cards.active().pan()).replace("NONE", TestData.nonExistentPan());
        String rollbackRrn = rrn.replace("RRN", TestData.uniqueRrn());

        given()
            .spec(ApiSpecs.gateway())
            .body(rollback(target, amount, rollbackRrn))
        .when()
            .post("/api/cards/{pan}/rollback", target)
        .then()
            .spec(ErrorBody.cardManagement(status, error,
                message.replace("{rrn}", rollbackRrn).replace("{last4}", TestData.last4(target))));
    }

    /** Откат обязан вернуть зарезервированную сумму. Сумма из запроса не сверяется, баланс растёт. */
    @Test
    @KnownDiscrepancy("D-09")
    void shouldCreateMoneyWhenRollbackAmountExceedsReservedAmount() {
        String pan = Cards.withBalance(1_000).pan();
        String rrn = TestData.uniqueRrn();
        Cards.reserve(pan, 100, rrn);

        given()
            .spec(ApiSpecs.gateway())
            .body(rollback(pan, 999, rrn))
        .when()
            .post("/api/cards/{pan}/rollback", pan)
        .then()
            .statusCode(200)
            .body("availableBalance", equalTo(1_899));
    }

    private static Map<String, Object> rollback(String pan, long amount, String rrn) {
        return Map.of("rrn", rrn, "pan", pan, "amount", amount);
    }
}
