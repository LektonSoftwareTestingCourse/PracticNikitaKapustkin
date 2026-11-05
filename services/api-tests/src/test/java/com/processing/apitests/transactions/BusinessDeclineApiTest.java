package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.Cards.Card;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import java.time.YearMonth;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Исходы алгоритма авторизации: DeclineOutcome, AuthorizationResponse, tz/04 пункт 2.
 * Через Gateway клиент всегда видит 200. Код и причина проверяются вместе: код 05 общий у CARD_BLOCKED
 * и CARD_INACTIVE, код 96 - у RESERVATION_FAILED и SERVICE_UNAVAILABLE.
 */
class BusinessDeclineApiTest {

    /** Решение принимает Switch (BIN неизвестен) или Authorization (карты нет): различие видно по mti, D-01. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("panPaths")
    @KnownDiscrepancy("D-01")
    void shouldDeclineWithCardNotFound(String path, Supplier<String> pan, String mti) {
        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan.get()).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("mti", equalTo(mti))
            .body("status", equalTo("DECLINED"))
            .body("responseCode", equalTo("14"))
            .body("declineReason", equalTo("CARD_NOT_FOUND"))
            .body("$", hasKey("rrn"))
            .body("rrn", nullValue())
            .body("$", hasKey("authCode"))
            .body("authCode", nullValue());
    }

    @ParameterizedTest(name = "{0} -> {1}, {2}")
    @CsvSource({
        "INACTIVE, CARD_INACTIVE, 05",
        "BLOCKED,  CARD_BLOCKED,  05",
        "EXPIRED,  CARD_EXPIRED,  54"
    })
    void shouldDeclineByStatusAndKeepBalance(String status, String reason, String code) {
        String pan = Cards.withStatus(status).pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo("DECLINED"))
            .body("responseCode", equalTo(code))
            .body("declineReason", equalTo(reason));

        assertThat(Cards.balanceOf(pan), equalTo(Cards.BALANCE));
    }

    /** Второй путь к CARD_EXPIRED: статус ACTIVE, но дата из запроса позже срока. Граница - конец месяца expiryDate. */
    @ParameterizedTest(name = "{0} -> {2}, {3}")
    @MethodSource("expiryBoundary")
    void shouldCompareTransmissionTimeWithExpiryMonth(String moment, Function<YearMonth, String> time,
                                                      String status, String code, String reason) {
        Card card = Cards.active();

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(card.pan()).transmittedAt(time.apply(YearMonth.parse(card.expiryDate()))).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo(status))
            .body("responseCode", equalTo(code))
            .body("declineReason", equalTo(reason));
    }

    /** Путь через данные запроса: сумма на единицу больше баланса. */
    @Test
    void shouldDeclineWithInsufficientFundsWhenAmountExceedsBalance() {
        String pan = Cards.withBalance(1_000).pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(1_001).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("responseCode", equalTo("51"))
            .body("declineReason", equalTo("INSUFFICIENT_FUNDS"));

        assertThat(Cards.balanceOf(pan), equalTo(1_000L));
    }

    /** Путь через состояние карты: сумма обычная, баланс снижен запросом PATCH. */
    @Test
    void shouldDeclineWithInsufficientFundsWhenBalanceWasReduced() {
        String pan = Cards.active().pan();
        Cards.patch(pan, Map.of("availableBalance", 10));

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(11).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("responseCode", equalTo("51"))
            .body("declineReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    /** Дневной лимит 1000, месячный 1500. Лимит считается по дате transmissionDateTime, tz/04 пункт 4. */
    @ParameterizedTest(name = "1000 на {0}, затем {1} на {2} -> {3}, {4}")
    @CsvSource(nullValues = "NULL", value = {
        "2026-06-01, 1,    2026-06-01, DECLINED, 61, EXCEEDS_AMOUNT_LIMIT",
        "2026-06-01, 500,  2026-06-02, APPROVED, 00, NULL",
        "2026-06-01, 501,  2026-06-02, DECLINED, 61, EXCEEDS_AMOUNT_LIMIT",
        "2026-06-30, 1000, 2026-07-01, APPROVED, 00, NULL"
    })
    void shouldAccumulateLimitsByTransmissionDate(String firstDate, long amount, String secondDate,
                                                  String status, String code, String reason) {
        String pan = Cards.withLimits(1_000, 1_500).pan();
        Transactions.approve(TransactionBody.forPan(pan).amount(1_000).transmittedAt(firstDate + "T10:00:00Z"));

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(amount).transmittedAt(secondDate + "T10:00:00Z").asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo(status))
            .body("responseCode", equalTo(code))
            .body("declineReason", equalTo(reason));
    }

    /** Путь через состояние карты: лимит снижен запросом PATCH. */
    @Test
    void shouldDeclineWithLimitExceededWhenLimitWasReduced() {
        String pan = Cards.active().pan();
        Cards.patch(pan, Map.of("dailyLimit", 10));

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(11).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("responseCode", equalTo("61"))
            .body("declineReason", equalTo("EXCEEDS_AMOUNT_LIMIT"));
    }

    /** tz/04: сначала лимиты, потом баланс, ожидался бы 61. Код проверяет баланс первым. */
    @Test
    @KnownDiscrepancy("D-12")
    void shouldReportInsufficientFundsWhenBothBalanceAndLimitAreExceeded() {
        String pan = Cards.create(500, 1_000, 1_000).pan();

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(2_000).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("responseCode", equalTo("51"))
            .body("declineReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    /** RRN - счётчик, поэтому следующий номер известен: занимаем его на карте заранее, и CMS отвечает 409. */
    @Test
    @KnownDiscrepancy("D-34")
    void shouldDeclineWithReservationFailedWhenRrnIsAlreadyTakenOnCard() {
        String pan = Cards.active().pan();
        takeNextRrnOn(pan);

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(100).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo("DECLINED"))
            .body("responseCode", equalTo("96"))
            .body("declineReason", equalTo("RESERVATION_FAILED"));
    }

    /** tz/04: лимит учитывает только успешные авторизации. После провала резерва он остаётся списанным. */
    @Test
    @KnownDiscrepancy("D-10")
    void shouldKeepLimitConsumedWhenReservationFailed() {
        String pan = Cards.withLimits(1_000, 100_000).pan();
        takeNextRrnOn(pan);
        Transactions.submit(TransactionBody.forPan(pan).amount(1_000));

        given()
            .spec(ApiSpecs.gateway())
            .body(TransactionBody.forPan(pan).amount(1).asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("responseCode", equalTo("61"))
            .body("declineReason", equalTo("EXCEEDS_AMOUNT_LIMIT"));
    }

    /** Лекция 10: в пути авторизации дедупликации по STAN нет. Повтор запроса списывает деньги дважды. */
    @Test
    @KnownDiscrepancy("D-13")
    void shouldChargeTwiceWhenSameRequestIsRepeated() {
        String pan = Cards.withBalance(1_000).pan();
        TransactionBody transaction = TransactionBody.forPan(pan).amount(100);

        String first = Transactions.approve(transaction);

        given()
            .spec(ApiSpecs.gateway())
            .body(transaction.asMap())
        .when()
            .post("/api/transactions")
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"))
            .body("rrn", not(equalTo(first)));

        assertThat(Cards.balanceOf(pan), equalTo(800L));
    }

    static Stream<Arguments> panPaths() {
        Supplier<String> unknownBin = TestData::panWithUnknownBin;
        Supplier<String> noCard = TestData::nonExistentPan;
        Supplier<String> deleted = () -> {
            String pan = Cards.active().pan();
            Cards.delete(pan);
            return pan;
        };
        return Stream.of(
                Arguments.of("BIN неизвестен, решает Switch", unknownBin, "0110"),
                Arguments.of("карты нет, решает Authorization", noCard, "0100"),
                Arguments.of("карта удалена", deleted, "0100"));
    }

    static Stream<Arguments> expiryBoundary() {
        Function<YearMonth, String> lastSecond = month -> month.atEndOfMonth() + "T23:59:59Z";
        Function<YearMonth, String> nextMonth = month -> month.plusMonths(1) + "-01T00:00:00Z";
        return Stream.of(
                Arguments.of("последняя секунда срока", lastSecond, "APPROVED", "00", null),
                Arguments.of("первая секунда после срока", nextMonth, "DECLINED", "54", "CARD_EXPIRED"));
    }

    private void takeNextRrnOn(String pan) {
        String lastRrn = Transactions.approve(TransactionBody.forPan(Cards.active().pan()).amount(1));
        Cards.reserve(pan, 1, String.format("%012d", Long.parseLong(lastRrn) + 1));
    }
}
