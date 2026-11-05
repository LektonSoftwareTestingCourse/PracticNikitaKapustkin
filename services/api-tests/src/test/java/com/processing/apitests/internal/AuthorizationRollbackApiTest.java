package com.processing.apitests.internal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Откат участвует в цепочке: Switch вызывает его при отказе брокера. В спецификации эндпоинта нет,
 * tz/03 описывает откат как reversal с mti=0400 (D-21, D-32). Ожидания: AuthControllerImpl.rollback, DeclineOutcome.
 */
class AuthorizationRollbackApiTest {

    @ParameterizedTest(name = "{0} -> HTTP {2}")
    @MethodSource("rollbacks")
    @KnownDiscrepancy({"D-21", "D-32"})
    void shouldMapRollbackOutcomeToHttpStatus(String outcome, Supplier<Map<String, Object>> request, int http,
                                              String field, String value) {
        given()
            .spec(ApiSpecs.authorization())
            .body(request.get())
        .when()
            .post("/api/internal/rollback")
        .then()
            .statusCode(http)
            .body(field, equalTo(value));
    }

    static Stream<Arguments> rollbacks() {
        Supplier<Map<String, Object>> approved = () -> approvedTransaction();
        Supplier<Map<String, Object>> repeated = () -> {
            Map<String, Object> request = approvedTransaction();
            given().spec(ApiSpecs.authorization()).body(request).post("/api/internal/rollback").then().statusCode(200);
            return request;
        };
        Supplier<Map<String, Object>> unknown = () ->
                Map.of("rrn", TestData.uniqueRrn(), "pan", Cards.active().pan(), "amount", 100);
        Supplier<Map<String, Object>> invalid = () -> Map.of("rrn", "12", "pan", "123", "amount", 0);
        return Stream.of(
                Arguments.of("одобренная транзакция", approved, 200, "status", "APPROVED"),
                Arguments.of("повторный откат", repeated, 409, "declineReason", "ALREADY_ROLLED_BACK"),
                Arguments.of("неизвестный RRN", unknown, 404, "declineReason", "TRANSACTION_NOT_FOUND"),
                Arguments.of("невалидный запрос", invalid, 400, "error", "Bad Request"));
    }

    private static Map<String, Object> approvedTransaction() {
        String pan = Cards.active().pan();
        String rrn = Transactions.approve(TransactionBody.forPan(pan).amount(400));
        return Map.of("rrn", rrn, "pan", pan, "amount", 400);
    }
}
