package com.processing.apitests.support;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.path.json.JsonPath;

import java.time.Duration;

/**
 * Шаги сценариев и ожидание асинхронных эффектов.
 * Границы ожидания из лекции 10: предел 15 с, первая проверка через 500 мс, дальше раз в секунду.
 * Публикация в очередь и запись Logger занимают доли секунды, поэтому интервала в секунду достаточно.
 */
public final class Transactions {

    public static final Duration ASYNC_LIMIT = Duration.ofSeconds(15);
    public static final Duration FIRST_CHECK = Duration.ofMillis(500);
    public static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    /** Окно, в течение которого эффект не должен наступить: запоздавшая запись пришла бы за доли секунды. */
    public static final Duration ABSENCE_WINDOW = Duration.ofSeconds(2);
    /** Сверх окна - запас на сам запрос поиска: после всплеска RateLimitApiTest он отвечал дольше 3 секунд. */
    public static final Duration ABSENCE_LIMIT = ABSENCE_WINDOW.plusSeconds(8);

    private Transactions() {
    }

    public static JsonPath submit(TransactionBody body) {
        return given().spec(ApiSpecs.gateway()).body(body.asMap())
                .when().post("/api/transactions")
                .then().statusCode(200).extract().jsonPath();
    }

    public static String approve(TransactionBody body) {
        JsonPath response = submit(body);
        if (!"APPROVED".equals(response.getString("status"))) {
            throw new IllegalStateException("Precondition failed, transaction declined: " + response.prettify());
        }
        return response.getString("rrn");
    }

    public static void log(LogRecord record) {
        given().spec(ApiSpecs.logger()).body(record.asMap())
                .when().post("/api/internal/log")
                .then().statusCode(201);
    }

    /** Ждёт ровно одну запись с этим STAN: фильтра stan в поиске нет, отбор идёт GPath внутри выборки по PAN. */
    public static void awaitStored(String pan, String stan) {
        awaitStored(pan, stan, ASYNC_LIMIT);
    }

    public static void awaitStored(String pan, String stan, Duration limit) {
        await().atMost(limit).pollDelay(FIRST_CHECK).pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> given().spec(ApiSpecs.gateway())
                        .queryParam("pan", pan)
                        .when().get("/api/transactions/search")
                        .then().statusCode(200)
                        .body("transactions.findAll { it.stan == '" + stan + "' }", hasSize(1)));
    }

    public static void assertTotalStored(String pan, int expectedTotal) {
        given().spec(ApiSpecs.gateway())
                .queryParam("pan", pan)
                .when().get("/api/transactions/search")
                .then().statusCode(200)
                .body("total", equalTo(expectedTotal));
    }
}
