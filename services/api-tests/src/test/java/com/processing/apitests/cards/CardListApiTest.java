package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Источник: GET /api/cards в openapi.yaml, CardController.getCards. Запросы идут в CMS, минуя кеш Gateway. */
class CardListApiTest {

    private static final String OWN_BIN = "400001";
    private static final String OWN_ISSUER = "ISS002";
    private static final String OTHER_BIN = "400002";
    private static final String OTHER_ISSUER = "ISS003";

    /**
     * Попарное покрытие: каждая пара значений встречается в одной строке. Своих карт три: одна BLOCKED, две ACTIVE.
     * Считаются только они, чужие карты в той же выборке на результат не влияют.
     */
    @ParameterizedTest(name = "status={0} bin={1} issuer={2} -> {3}")
    @CsvSource(nullValues = "-", value = {
        "ACTIVE,  own,   own,   2",
        "ACTIVE,  other, other, 0",
        "ACTIVE,  -,     -,     2",
        "BLOCKED, own,   -,     1",
        "BLOCKED, other, own,   0",
        "BLOCKED, -,     other, 0",
        "-,       own,   other, 0",
        "-,       other, -,     0",
        "-,       -,     own,   3"
    })
    void shouldCombineFiltersWithAnd(String status, String bin, String issuer, int expected) {
        List<String> pans = IntStream.range(0, 3)
                .mapToObj(i -> Cards.create(Cards.requestBody(OWN_BIN, 1_000, 1_000, 1_000)).pan())
                .toList();
        Cards.patch(pans.get(0), Map.of("status", "BLOCKED"));
        Map<String, String> filters = new HashMap<>();
        filters.put("status", status);
        filters.put("bin", Map.of("own", OWN_BIN, "other", OTHER_BIN).get(String.valueOf(bin)));
        filters.put("issuerId", Map.of("own", OWN_ISSUER, "other", OTHER_ISSUER).get(String.valueOf(issuer)));
        filters.values().removeIf(Objects::isNull);

        given()
            .spec(ApiSpecs.cardManagement())
            .queryParams(filters)
            .queryParam("limit", 10_000)
        .when()
            .get("/api/cards")
        .then()
            .statusCode(200)
            .body("cards.findAll { [" + quoted(pans) + "].contains(it.pan) }", hasSize(expected));
    }

    private static String quoted(List<String> pans) {
        return pans.stream().map(pan -> "'" + pan + "'").collect(Collectors.joining(","));
    }

    /** Граница limit: minimum=1 в openapi.yaml, max-page-limit=10000. Статус 413 не объявлен (D-24), 500 - D-07. */
    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "limit, 1",
        "limit, 10000",
        "offset, 0",
        "startDate, 2000-01-01T00:00:00Z",
        "endDate, 2099-01-01T00:00:00Z"
    })
    void shouldAcceptBoundaryValueOfQueryParameter(String parameter, String value) {
        given()
            .spec(ApiSpecs.cardManagement())
            .queryParam(parameter, value)
        .when()
            .get("/api/cards")
        .then()
            .statusCode(200)
            .body("$", hasKey("cards"))
            .body("error", nullValue());
    }

    /** Сообщение начинается с имени параметра: getCards.limit, Method parameter 'status' и так далее. */
    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource({
        "limit, 0, 400, ConstraintViolationException, 'getCards.limit: must be greater than 0'",
        "limit, 10001, 413, TooLargeLimitException, Page limit (10001 received) can not be more than 10000",
        "offset, -1, 400, ConstraintViolationException, 'getCards.offset: Value can not be negative'",
        "bin, 12, 400, ConstraintViolationException, 'getCards.bin: BIN number must contain exactly 6 digits'",
        "issuerId, abc, 400, ConstraintViolationException, 'getCards.issuerId: must match'",
        "status, NOPE, 500, MethodArgumentTypeMismatchException, 'Method parameter ''status'': Failed to convert'",
        "startDate, bad, 500, MethodArgumentTypeMismatchException, 'Method parameter ''startDate'': Failed to convert'"
    })
    @KnownDiscrepancy({"D-07", "D-24"})
    void shouldRejectInvalidQueryParameter(String parameter, String value, int status, String error, String message) {
        given()
            .spec(ApiSpecs.cardManagement())
            .queryParam(parameter, value)
        .when()
            .get("/api/cards")
        .then()
            .spec(ErrorBody.cardManagement(status, error))
            .body("message", startsWith(message));
    }
}
