package com.processing.apitests.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Тело POST /api/transactions. Значения по умолчанию проходят все проверки цепочки:
 * terminalId из 8 и merchantId из 15 символов не меняются нормализатором Switch.
 * {@link #without(String)} убирает поле, {@code with(field, null)} передаёт JSON null - это разные классы.
 */
public final class TransactionBody {

    /** Прошлая дата: раньше срока любой новой карты. Лимиты привязаны к PAN, поэтому дата не пересекается между тестами. */
    public static final String DEFAULT_TRANSMISSION_TIME = "2026-06-01T10:30:00Z";
    public static final long DEFAULT_AMOUNT = 150_000L;

    private final Map<String, Object> fields = new LinkedHashMap<>();

    private TransactionBody() {
    }

    public static TransactionBody forPan(String pan) {
        return new TransactionBody()
                .with("mti", "0100")
                .with("stan", TestData.uniqueStan())
                .with("pan", pan)
                .with("processingCode", "000000")
                .with("amount", DEFAULT_AMOUNT)
                .with("currencyCode", "643")
                .with("transmissionDateTime", DEFAULT_TRANSMISSION_TIME)
                .with("terminalId", "TERM0001")
                .with("terminalType", "POS")
                .with("merchantId", "MERCH0000000001")
                .with("mcc", "5411")
                .with("acquirerId", "ACQ001");
    }

    public TransactionBody with(String field, Object value) {
        fields.put(field, value);
        return this;
    }

    public TransactionBody without(String field) {
        fields.remove(field);
        return this;
    }

    public TransactionBody amount(Object amount) {
        return with("amount", amount);
    }

    public TransactionBody transmittedAt(String isoInstant) {
        return with("transmissionDateTime", isoInstant);
    }

    /** Вид, который ждёт /api/internal/authorize: его заполняет Switch. */
    public TransactionBody routedToIssuer() {
        return with("issuerId", TestData.KNOWN_ISSUER);
    }

    public String stan() {
        return (String) fields.get("stan");
    }

    public Map<String, Object> asMap() {
        return fields;
    }
}
