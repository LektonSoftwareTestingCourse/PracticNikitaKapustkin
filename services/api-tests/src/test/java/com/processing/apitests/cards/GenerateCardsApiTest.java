package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.oneOf;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Источник: POST /api/cards/generate в openapi.yaml: равномерно по BIN, месячный лимит = дневной x 30. */
class GenerateCardsApiTest {

    @Test
    void shouldGenerateCardsWithinDeclaredRanges() {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of("count", 4, "bins", List.of("400000", "400001")))
        .when()
            .post("/api/cards/generate")
        .then()
            .statusCode(201)
            .body("generated", equalTo(4))
            .body("cards.findAll { it.bin == '400000' }", hasSize(2))
            .body("cards.findAll { it.bin == '400001' }", hasSize(2))
            .body("cards.findAll { it.monthlyLimit == it.dailyLimit * 30 }", hasSize(4))
            .body("cards.status", everyItem(oneOf("ACTIVE", "INACTIVE", "BLOCKED")))
            .body("cards.availableBalance", everyItem(greaterThanOrEqualTo(1_000_000)))
            .body("cards.availableBalance", everyItem(lessThan(50_000_000)));
    }

    /** Статус 404 на незарегистрированный BIN в openapi.yaml не объявлен. */
    @ParameterizedTest(name = "count={0} bin={1} -> {2}")
    @CsvSource({
        "0, 400000, 400, MethodArgumentNotValidException, must be greater than or equal to 1",
        "10001, 400000, 400, CardGenerationLimitException, 'Count exceeds maximum value: 10000'",
        "1, 12, 400, MethodArgumentNotValidException, BIN number must contain exactly 6 digits",
        "1, 999999, 404, BinNotFoundException, 'BIN not found: Unknown BIN: 999999'"
    })
    @KnownDiscrepancy({"D-17", "D-24"})
    void shouldRejectGeneration(int count, String bin, int status, String error, String message) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Map.of("count", count, "bins", List.of(bin)))
        .when()
            .post("/api/cards/generate")
        .then()
            .spec(ErrorBody.cardManagement(status, error, message));
    }
}
