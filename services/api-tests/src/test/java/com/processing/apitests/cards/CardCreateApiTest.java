package com.processing.apitests.cards;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.Cards;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.KnownDiscrepancy;
import com.processing.apitests.support.Luhn;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Источник: POST /api/cards в docs/api-spec.md и openapi.yaml, CreateCardRequest, GlobalExceptionHandler CMS. */
class CardCreateApiTest {

    /** Таблица BIN: application.yml Switch. Срок: openapi.yaml, текущая дата + 3 года, дата берётся из createdAt. */
    @ParameterizedTest(name = "BIN {0} -> {1}")
    @CsvSource({"400000, ISS001", "400001, ISS002", "400002, ISS003", "400003, ISS004", "400004, ISS005"})
    @KnownDiscrepancy("D-03")
    void shouldCreateActiveCardWithIssuerOfBin(String bin, String issuer) {
        JsonPath card = given()
            .spec(ApiSpecs.gateway())
            .body(Cards.requestBody(bin, 100_000_000, 15_000_000, 300_000_000))
        .when()
            .post("/api/cards")
        .then()
            .statusCode(201)
            .body(matchesJsonSchemaInClasspath("schemas/card.json"))
            .body("pan", matchesPattern(bin + "\\d{10}"))
            .body("status", equalTo("ACTIVE"))
            .body("issuerId", equalTo(issuer))
            .body("dailyLimit", equalTo(15_000_000))
            .body("monthlyLimit", equalTo(300_000_000))
            .body("availableBalance", equalTo(100_000_000))
            .extract().jsonPath();

        String createdAt = card.getString("createdAt");
        int year = Integer.parseInt(createdAt.substring(0, 4));
        assertThat(card.getString("expiryDate"), equalTo((year + 3) + "-" + createdAt.substring(5, 7)));
        assertThat("Luhn", Luhn.isValid(card.getString("pan")));
    }

    /** openapi.yaml: у currencyCode есть default, но без него ответ 400 (D-08). */
    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
        "bin, 40000, 400, MethodArgumentNotValidException, BIN number must contain exactly 6 digits",
        "bin, 4000000, 400, MethodArgumentNotValidException, BIN number must contain exactly 6 digits",
        "bin, 40000A, 400, MethodArgumentNotValidException, BIN number must contain exactly 6 digits",
        "bin, NULL, 400, MethodArgumentNotValidException, must not be blank",
        "bin, 999999, 404, BinNotFoundException, BIN not found: Unknown BIN: 999999",
        "cardholderName, ivan, 400, MethodArgumentNotValidException, "
            + "'Cardholder name can contain only uppercase letters, spaces, '', dots and -'",
        "cardholderName, NULL, 400, MethodArgumentNotValidException, must not be blank",
        "currencyCode, 64, 400, MethodArgumentNotValidException, The length must be exactly 3 symbols",
        "currencyCode, ABC, 400, MethodArgumentNotValidException, Value can contain digits only",
        "currencyCode, NULL, 400, MethodArgumentNotValidException, must not be blank",
        "dailyLimit, -1, 400, MethodArgumentNotValidException, Value can not be negative",
        "monthlyLimit, -1, 400, MethodArgumentNotValidException, Value can not be negative"
    })
    @KnownDiscrepancy("D-08")
    void shouldRejectCardWhenFieldBreaksRule(String field, String value, int status, String error, String message) {
        Map<String, Object> body = Cards.requestBody("400000", 1, 1, 1);
        body.put(field, value);

        given()
            .spec(ApiSpecs.gateway())
            .body(body)
        .when()
            .post("/api/cards")
        .then()
            .spec(ErrorBody.cardManagement(status, error, message));
    }

    /** PATCH отклоняет monthlyLimit меньше dailyLimit, POST то же сочетание принимает; отрицательный баланс тоже (D-27). */
    @ParameterizedTest(name = "balance={0} daily={1} monthly={2}")
    @CsvSource({"10, 100, 1", "-1, 100, 1000"})
    @KnownDiscrepancy("D-27")
    void shouldAcceptCardThatPatchRulesWouldReject(long balance, long daily, long monthly) {
        given()
            .spec(ApiSpecs.gateway())
            .body(Cards.requestBody("400000", balance, daily, monthly))
        .when()
            .post("/api/cards")
        .then()
            .statusCode(201)
            .body("availableBalance", equalTo((int) balance))
            .body("dailyLimit", equalTo((int) daily))
            .body("monthlyLimit", equalTo((int) monthly));
    }
}
