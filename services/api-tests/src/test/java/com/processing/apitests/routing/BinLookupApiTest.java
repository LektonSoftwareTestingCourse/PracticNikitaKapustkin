package com.processing.apitests.routing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.KnownDiscrepancy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Authorization вызывает Bin Lookup на каждой авторизации: tz/04, вариант B. В спецификации сервиса нет,
 * маршрута в Gateway тоже, поэтому вызов напрямую на порт 8096. Параметр fail имитирует сбой, тело ошибок пустое.
 */
class BinLookupApiTest {

    @ParameterizedTest(name = "bin={0} fail={1} -> {2}")
    @CsvSource(delimiter = '|', value = {
        "400000 | false | 200 | {\"bin\":\"400000\",\"issuerId\":\"ISS001\",\"issuerName\":\"Test Bank 1\"}",
        "999999 | false | 404 | ''",
        "400000 | true  | 500 | ''"
    })
    @KnownDiscrepancy("D-21")
    void shouldReturnIssuerOfBin(String bin, boolean fail, int status, String body) {
        given()
            .spec(ApiSpecs.binLookup())
            .queryParam("fail", fail)
        .when()
            .get("/api/bin/{bin}", bin)
        .then()
            .statusCode(status)
            .body(equalTo(body));
    }
}
