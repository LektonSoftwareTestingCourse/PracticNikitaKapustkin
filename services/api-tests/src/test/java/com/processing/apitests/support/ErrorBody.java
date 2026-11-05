package com.processing.apitests.support;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.ResponseSpecification;

/** Форматы ошибок по сервисам. serviceName отвечает на вопрос, кто вынес отказ. */
public final class ErrorBody {

    private ErrorBody() {
    }

    /** ErrorResponse Gateway: TransactionValidationFilter.writeValidationError. */
    public static ResponseSpecification gatewayValidation(String message) {
        return new ResponseSpecBuilder()
                .expectStatusCode(400)
                .expectBody("error", equalTo("VALIDATION_ERROR"))
                .expectBody("message", equalTo(message))
                .expectBody("serviceName", equalTo("gateway"))
                .expectBody("timestamp", notNullValue())
                .expectBody("retryAfterMs", nullValue())
                .build();
    }

    /** ErrorResponse CMS: GlobalExceptionHandler, error - имя класса исключения. */
    public static ResponseSpecification cardManagement(int status, String exception, String message) {
        return new ResponseSpecBuilder()
                .addResponseSpecification(cardManagement(status, exception))
                .expectBody("message", equalTo(message))
                .build();
    }

    /** Для длинных сообщений Spring: текст проверяется в тесте по началу, которое называет поле. */
    public static ResponseSpecification cardManagement(int status, String exception) {
        return new ResponseSpecBuilder()
                .expectStatusCode(status)
                .expectBody("error", equalTo(exception))
                .expectBody("serviceName", equalTo("card-management"))
                .expectBody("timestamp", notNullValue())
                .build();
    }

    /** ServiceUnavailableResponse Gateway: DownstreamErrorFilter и открытая цепь пишут имя с разным регистром (D-29). */
    public static ResponseSpecification serviceUnavailable(String serviceName) {
        return new ResponseSpecBuilder()
                .expectStatusCode(503)
                .expectBody("error", equalTo("SERVICE_UNAVAILABLE"))
                .expectBody("message", equalToIgnoringCase(serviceName + " service is temporarily unavailable"))
                .expectBody("serviceName", equalTo(serviceName))
                .build();
    }

    /** Ответ Spring по умолчанию: маршрут без metadata.serviceName DownstreamErrorFilter не распознаёт (D-36). */
    public static ResponseSpecification springDefault(int status, String error, String path) {
        return new ResponseSpecBuilder()
                .expectStatusCode(status)
                .expectBody("status", equalTo(status))
                .expectBody("error", equalTo(error))
                .expectBody("path", equalTo(path))
                .expectBody("timestamp", notNullValue())
                .build();
    }

    /** ErrorResponse Logger: GlobalExceptionHandler. */
    public static ResponseSpecification logger(int status, String error) {
        return new ResponseSpecBuilder()
                .expectStatusCode(status)
                .expectBody("error", equalTo(error))
                .expectBody("serviceName", equalTo("transaction-logger"))
                .expectBody("timestamp", notNullValue())
                .expectBody("retryAfterMs", equalTo("0"))
                .build();
    }
}
