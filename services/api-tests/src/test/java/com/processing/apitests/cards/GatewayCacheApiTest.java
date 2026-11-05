package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.TransactionBody;
import com.processing.apitests.support.Transactions;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Источник: лекция 9, заголовок X-Cache; ResponseCachingGatewayFilterFactory. */
class GatewayCacheApiTest {

    @Test
    void shouldCacheCardUntilItIsPatched() {
        String pan = Cards.active().pan();

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .header("X-Cache", "MISS");

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .header("X-Cache", "HIT");

        Cards.patch(pan, Map.of("status", "BLOCKED"));

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .header("X-Cache", "MISS")
            .body("status", equalTo("BLOCKED"));
    }

    /** Резерв идёт мимо Gateway, а кеш чистится только на 200 и 201, DELETE отвечает 204. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("changesMissedByCache")
    @KnownDiscrepancy("D-11")
    void shouldServeStaleCardWhenChangeBypassesCache(String change, Consumer<String> action) {
        String pan = Cards.active().pan();
        given().spec(ApiSpecs.gateway()).when().get("/api/cards/{pan}", pan).then().statusCode(200);

        action.accept(pan);

        given()
            .spec(ApiSpecs.gateway())
        .when()
            .get("/api/cards/{pan}", pan)
        .then()
            .statusCode(200)
            .header("X-Cache", "HIT")
            .body("availableBalance", equalTo((int) Cards.BALANCE));
    }

    static Stream<Arguments> changesMissedByCache() {
        Consumer<String> approve = pan -> Transactions.approve(TransactionBody.forPan(pan));
        Consumer<String> delete = Cards::delete;
        return Stream.of(Arguments.of("транзакция", approve), Arguments.of("удаление", delete));
    }
}
