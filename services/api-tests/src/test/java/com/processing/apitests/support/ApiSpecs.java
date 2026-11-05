package com.processing.apitests.support;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Единственное место, где заданы адреса сервисов и общие заголовки.
 * Внешний контракт проверяется через Gateway, внутренние эндпоинты и эндпоинты без маршрута - напрямую.
 * Порты: docs/architecture.md, раздел «Порты сервисов»; переопределяются -Dapi.<сервис>.url.
 */
public final class ApiSpecs {

    private static final String HOST = "http://localhost:";

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private ApiSpecs() {
    }

    public static RequestSpecification gateway() {
        return spec("gateway", 8080);
    }

    public static RequestSpecification cardManagement() {
        return spec("cards", 8081);
    }

    public static RequestSpecification switchService() {
        return spec("switch", 8082);
    }

    public static RequestSpecification authorization() {
        return spec("authorization", 8083);
    }

    public static RequestSpecification merchantSimulator() {
        return spec("merchant", 8084);
    }

    public static RequestSpecification terminalSimulator() {
        return spec("terminal", 8085);
    }

    public static RequestSpecification logger() {
        return spec("logger", 8088);
    }

    public static RequestSpecification binLookup() {
        return spec("bin-lookup", 8096);
    }

    /** Management API брокера: учётная запись по умолчанию из docker-compose.yaml. */
    public static RequestSpecification rabbitManagement() {
        return new RequestSpecBuilder()
                .setBaseUri(System.getProperty("api.rabbit.url", HOST + 15672))
                .setAuth(RestAssured.preemptive().basic(
                        System.getProperty("api.rabbit.user", "smp"),
                        System.getProperty("api.rabbit.password", "smp")))
                .setUrlEncodingEnabled(false)
                .build();
    }

    private static RequestSpecification spec(String service, int port) {
        return new RequestSpecBuilder()
                .setBaseUri(System.getProperty("api." + service + ".url", HOST + port))
                .setContentType(ContentType.JSON)
                .addFilter(new CoverageFilter())
                .build();
    }
}
