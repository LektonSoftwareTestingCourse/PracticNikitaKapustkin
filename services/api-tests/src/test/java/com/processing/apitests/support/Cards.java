package com.processing.apitests.support;

import static io.restassured.RestAssured.given;

import java.util.HashMap;
import java.util.Map;

/**
 * Подготовка карт только через API. Состояние всегда задано явно, карта создаётся под один тест.
 * Запись идёт через Gateway, чтение состояния - напрямую из CMS: ответы GET /api/cards/** кешируются в Gateway.
 */
public final class Cards {

    public static final long BALANCE = 100_000_000L;
    public static final long DAILY_LIMIT = 15_000_000L;
    public static final long MONTHLY_LIMIT = 300_000_000L;

    private Cards() {
    }

    public static Card active() {
        return create(BALANCE, DAILY_LIMIT, MONTHLY_LIMIT);
    }

    public static Card withBalance(long balance) {
        return create(balance, DAILY_LIMIT, MONTHLY_LIMIT);
    }

    public static Card withLimits(long dailyLimit, long monthlyLimit) {
        return create(BALANCE, dailyLimit, monthlyLimit);
    }

    public static Card withStatus(String status) {
        Card card = active();
        patch(card.pan(), Map.of("status", status));
        return card;
    }

    public static Card create(long balance, long dailyLimit, long monthlyLimit) {
        return create(requestBody(TestData.KNOWN_BIN, balance, dailyLimit, monthlyLimit));
    }

    public static Card create(Map<String, Object> body) {
        var response = given().spec(ApiSpecs.gateway()).body(body)
                .when().post("/api/cards")
                .then().statusCode(201).extract().jsonPath();
        return new Card(response.getString("pan"), response.getString("expiryDate"));
    }

    public static Map<String, Object> requestBody(String bin, long balance, long dailyLimit, long monthlyLimit) {
        Map<String, Object> body = new HashMap<>();
        body.put("bin", bin);
        body.put("cardholderName", "IVAN IVANOV");
        body.put("currencyCode", "643");
        body.put("dailyLimit", dailyLimit);
        body.put("monthlyLimit", monthlyLimit);
        body.put("initialBalance", balance);
        return body;
    }

    public static void patch(String pan, Map<String, Object> changes) {
        given().spec(ApiSpecs.gateway()).body(changes)
                .when().patch("/api/cards/{pan}", pan)
                .then().statusCode(200);
    }

    public static void delete(String pan) {
        given().spec(ApiSpecs.gateway())
                .when().delete("/api/cards/{pan}", pan)
                .then().statusCode(204);
    }

    public static void reserve(String pan, long amount, String rrn) {
        given().spec(ApiSpecs.cardManagement()).body(Map.of("amount", amount, "rrn", rrn))
                .when().post("/api/cards/{pan}/reserve", pan)
                .then().statusCode(200);
    }

    public static long balanceOf(String pan) {
        return given().spec(ApiSpecs.cardManagement())
                .when().get("/api/cards/{pan}", pan)
                .then().statusCode(200).extract().jsonPath().getLong("availableBalance");
    }

    public record Card(String pan, String expiryDate) {
    }
}
