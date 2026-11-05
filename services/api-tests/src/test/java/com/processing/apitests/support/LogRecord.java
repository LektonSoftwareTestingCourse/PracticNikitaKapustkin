package com.processing.apitests.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Тело POST /api/internal/log: полный объект Transaction с заранее известными значениями. */
public final class LogRecord {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    private LogRecord() {
    }

    public static LogRecord approved(String pan) {
        return new LogRecord()
                .with("id", TestData.uniqueId())
                .with("mti", "0100")
                .with("stan", TestData.uniqueStan())
                .with("rrn", TestData.uniqueRrn())
                .with("pan", pan)
                .with("processingCode", "000000")
                .with("amount", 150_000)
                .with("currencyCode", "643")
                .with("terminalId", "TERM0001")
                .with("terminalType", "POS")
                .with("merchantId", "MERCH0000000001")
                .with("mcc", "5411")
                .with("acquirerId", "ACQ001")
                .with("issuerId", TestData.KNOWN_ISSUER)
                .with("status", "APPROVED")
                .with("authCode", "ABC123")
                .with("processingTimeMs", 5)
                .with("transmissionDateTime", TransactionBody.DEFAULT_TRANSMISSION_TIME)
                .with("createdAt", "2026-06-01T10:30:01Z");
    }

    public static LogRecord declined(String pan, String reason) {
        return approved(pan)
                .with("status", "DECLINED")
                .with("declineReason", reason)
                .with("rrn", null)
                .with("authCode", null);
    }

    public LogRecord with(String field, Object value) {
        fields.put(field, value);
        return this;
    }

    public LogRecord without(String field) {
        fields.remove(field);
        return this;
    }

    public String id() {
        return (String) fields.get("id");
    }

    public String stan() {
        return (String) fields.get("stan");
    }

    public Map<String, Object> asMap() {
        return fields;
    }

    /** Копия с теми же значениями: повторная доставка того же сообщения. */
    public LogRecord copy() {
        LogRecord copy = new LogRecord();
        copy.fields.putAll(fields);
        return copy;
    }
}
