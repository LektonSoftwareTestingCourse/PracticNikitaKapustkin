package com.processing.apitests.transactions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import com.processing.apitests.support.ApiSpecs;
import com.processing.apitests.support.ErrorBody;
import com.processing.apitests.support.LogRecord;
import com.processing.apitests.support.TestData;
import com.processing.apitests.support.Transactions;
import org.junit.jupiter.api.Test;

/** Источник: GET /api/transactions/export в openapi.yaml, лекция 9 (Content-Disposition), TransactionCsvWriter. */
class TransactionExportApiTest {

    /** Фильтры те же, что в поиске, limit не применяется: openapi.yaml. Значение с запятой экранируется. */
    @Test
    void shouldExportFilteredRowsAsCsvAttachment() {
        String pan = TestData.nonExistentPan();
        LogRecord declined = LogRecord.declined(pan, "Reason, with comma");
        Transactions.log(declined);
        Transactions.log(LogRecord.approved(pan));

        given()
            .spec(ApiSpecs.gateway())
            .queryParam("pan", pan)
            .queryParam("status", "DECLINED")
            .queryParam("offset", 5)
        .when()
            .get("/api/transactions/export")
        .then()
            .statusCode(200)
            .contentType("text/csv")
            .header("Content-Disposition", "attachment; filename=\"transactions.csv\"")
            .body(startsWith("id,mti,stan,rrn,pan,"))
            .body(containsString(declined.id()))
            .body(containsString("\"Reason, with comma\""))
            .body(not(containsString("APPROVED")));
    }

    @Test
    void shouldAnswer400WhenFilterCannotBeConverted() {
        given()
            .spec(ApiSpecs.gateway())
            .queryParam("status", "NOPE")
        .when()
            .get("/api/transactions/export")
        .then()
            .spec(ErrorBody.logger(400, "Validation error"))
            .body("message", startsWith("status: Failed to convert"));
    }
}
