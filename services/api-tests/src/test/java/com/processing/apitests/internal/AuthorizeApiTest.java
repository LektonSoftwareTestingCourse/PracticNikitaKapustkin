package com.processing.apitests.internal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Источник: POST /api/internal/authorize, AuthControllerImpl, лекция 10: внутренний сервис отвечает
 * 404, 403, 422, 503, а клиент через Gateway видит только 200. openapi.yaml объявляет только 200 (D-18).
 */
class AuthorizeApiTest {

    @ParameterizedTest(name = "{0} -> HTTP {2}")
    @MethodSource("outcomes")
    @KnownDiscrepancy({"D-18", "D-34"})
    void shouldMapOutcomeToHttpStatus(String outcome, Supplier<TransactionBody> transaction, int http,
                                      String reason, String code) {
        given()
            .spec(ApiSpecs.authorization())
            .body(transaction.get().routedToIssuer().asMap())
        .when()
            .post("/api/internal/authorize")
        .then()
            .statusCode(http)
            .body("declineReason", equalTo(reason))
            .body("responseCode", equalTo(code));
    }

    /** @ExactSize в AuthorizationRequest строже Gateway: длины выравнивает только Switch. Тело - формат Spring (D-17). */
    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"mti, 010", "stan, 12", "terminalId, TERM001", "merchantId, MERCH12345678901", "issuerId, ''"})
    @KnownDiscrepancy("D-17")
    void shouldAnswer400WhenFieldBreaksExactSize(String field, String value) {
        given()
            .spec(ApiSpecs.authorization())
            .body(TransactionBody.forPan(Cards.active().pan()).routedToIssuer().with(field, value).asMap())
        .when()
            .post("/api/internal/authorize")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"));
    }

    static Stream<Arguments> outcomes() {
        Supplier<TransactionBody> approved = () -> TransactionBody.forPan(Cards.active().pan());
        Supplier<TransactionBody> blocked = () -> TransactionBody.forPan(Cards.withStatus("BLOCKED").pan());
        Supplier<TransactionBody> noCard = () -> TransactionBody.forPan(TestData.nonExistentPan());
        Supplier<TransactionBody> noFunds = () -> TransactionBody.forPan(Cards.withBalance(1).pan()).amount(2);
        Supplier<TransactionBody> overLimit = () -> TransactionBody.forPan(Cards.withLimits(1, 1).pan()).amount(2);
        Supplier<TransactionBody> rrnTaken = () -> {
            String pan = Cards.active().pan();
            String lastRrn = Transactions.approve(TransactionBody.forPan(Cards.active().pan()).amount(1));
            Cards.reserve(pan, 1, String.format("%012d", Long.parseLong(lastRrn) + 1));
            return TransactionBody.forPan(pan).amount(100);
        };
        return Stream.of(
                Arguments.of("одобрено", approved, 200, null, "00"),
                Arguments.of("карта заблокирована", blocked, 403, "CARD_BLOCKED", "05"),
                Arguments.of("карты нет", noCard, 404, "CARD_NOT_FOUND", "14"),
                Arguments.of("нет средств", noFunds, 422, "INSUFFICIENT_FUNDS", "51"),
                Arguments.of("лимит", overLimit, 400, "EXCEEDS_AMOUNT_LIMIT", "61"),
                Arguments.of("ошибка резерва", rrnTaken, 503, "RESERVATION_FAILED", "96"));
    }
}
