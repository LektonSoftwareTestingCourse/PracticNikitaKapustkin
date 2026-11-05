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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Источник: POST /api/cards/{pan}/reserve в openapi.yaml, ReserveRequest, Card.startReservation. */
class ReserveApiTest {

    @ParameterizedTest(name = "amount={0}, balance=1000 -> {1}")
    @CsvSource({"300, 700", "1000, 0"})
    void shouldDecreaseBalanceWhenFundsAreReserved(long amount, int balance) {
        String pan = Cards.withBalance(1_000).pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(reserve(amount, TestData.uniqueRrn()))
        .when()
            .post("/api/cards/{pan}/reserve", pan)
        .then()
            .statusCode(200)
            .body("availableBalance", equalTo(balance));
    }

    /** OWN - карта теста с балансом 1000, NONE - уникальный PAN без карты, RRN - уникальный номер. rrn по контракту maxLength, по коду ровно 12. */
    @ParameterizedTest(name = "pan={0} amount={1} rrn={2} -> {3}")
    @CsvSource({
        "OWN, 1001, RRN, 402, InsufficientFundsException, Not enough funds on this account",
        "OWN, 0, RRN, 400, MethodArgumentNotValidException, must be greater than 0",
        "OWN, -1, RRN, 400, MethodArgumentNotValidException, must be greater than 0",
        "OWN, 100, 12345678901, 400, MethodArgumentNotValidException, RRN number must contain exactly 12 digits",
        "OWN, 100, 1234567890123, 400, MethodArgumentNotValidException, RRN number must contain exactly 12 digits",
        "OWN, 100, 12345678901A, 400, MethodArgumentNotValidException, RRN number must contain exactly 12 digits",
        "NONE, 100, RRN, 404, CardNotFoundException, Card with PAN 4000********{last4} was not found",
        "123, 100, RRN, 400, ConstraintViolationException, 'reserve.pan: PAN number must contain exactly 16 digits'"
    })
    @KnownDiscrepancy({"D-17", "D-24"})
    void shouldRejectReservationAndKeepBalance(String pan, long amount, String rrn, int status, String error,
                                               String message) {
        String own = Cards.withBalance(1_000).pan();
        String target = pan.replace("OWN", own).replace("NONE", TestData.nonExistentPan());

        given()
            .spec(ApiSpecs.gateway())
            .body(reserve(amount, rrn.replace("RRN", TestData.uniqueRrn())))
        .when()
            .post("/api/cards/{pan}/reserve", target)
        .then()
            .spec(ErrorBody.cardManagement(status, error, message.replace("{last4}", TestData.last4(target))));

        assertThat(Cards.balanceOf(own), equalTo(1_000L));
    }

    /** Статус 409 в openapi.yaml для reserve не объявлен. */
    @ParameterizedTest(name = "status={0} -> {1}")
    @CsvSource({
        "ACTIVE, ReservationAlreadyExistsException, "
            + "'Reservation with RRN number \"{rrn}\" and PAN \"4000********{last4}\" already exists in the database'",
        "INACTIVE, IllegalStateException, 'Can''t perform operation: card status is \"INACTIVE\"'",
        "BLOCKED, IllegalStateException, 'Can''t perform operation: card status is \"BLOCKED\"'",
        "EXPIRED, IllegalStateException, 'Can''t perform operation: card status is \"EXPIRED\"'"
    })
    @KnownDiscrepancy("D-24")
    void shouldAnswer409WhenRrnIsRepeatedOrCardIsNotActive(String status, String error, String message) {
        String pan = Cards.active().pan();
        String rrn = TestData.uniqueRrn();
        Cards.reserve(pan, 100, rrn);
        Cards.patch(pan, Map.of("status", status));

        given()
            .spec(ApiSpecs.gateway())
            .body(reserve(100, rrn))
        .when()
            .post("/api/cards/{pan}/reserve", pan)
        .then()
            .spec(ErrorBody.cardManagement(409, error,
                message.replace("{rrn}", rrn).replace("{last4}", TestData.last4(pan))));
    }

    private static Map<String, Object> reserve(long amount, String rrn) {
        return Map.of("amount", amount, "rrn", rrn);
    }
}
