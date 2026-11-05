package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.KnownDiscrepancy;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Источник: openapi.yaml, 429 у POST /api/transactions, tz/02 пункт 4: 100 токенов, 100 в секунду на IP.
 * У теста свой X-Forwarded-For, поэтому остальные тесты он не задевает. Заголовок никто не проверяет: D-30.
 * Тело пустое: фильтр лимита стоит раньше валидации, запросы не доходят до Switch.
 */
class RateLimitApiTest {

    private static final int BURST = 500;
    private static final int CAPACITY = 100;

    @Test
    @KnownDiscrepancy({"D-17", "D-30"})
    void shouldAnswer429AndRecoverWhenBurstExceedsCapacity() {
        String client = "10.1." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(1, 255);

        List<Response> responses = IntStream.range(0, BURST).parallel()
                .mapToObj(i -> given().spec(ApiSpecs.gateway()).header("X-Forwarded-For", client).body("{}")
                        .post("/api/transactions"))
                .toList();

        assertThat(responses.stream().filter(r -> r.statusCode() == 400).count(),
                greaterThanOrEqualTo((long) CAPACITY));
        responses.stream().filter(r -> r.statusCode() == 429).findFirst().orElseThrow().then()
            .header("Retry-After", "1")
            .body("error", equalTo("RATE_LIMIT_EXCEEDED"))
            .body("message", equalTo("Too many requests. Try again later."))
            .body("retryAfterMs", equalTo(1000))
            .body("$", not(hasKey("timestamp")));

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> given()
                .spec(ApiSpecs.gateway())
                .header("X-Forwarded-For", client)
                .body("{}")
            .when()
                .post("/api/transactions")
            .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR")));
    }
}
