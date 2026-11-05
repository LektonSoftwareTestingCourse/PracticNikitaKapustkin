package com.processing.apitests.support;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Уникальные идентификаторы на тест. Счётчик начинается со случайного значения на прогон,
 * поэтому STAN и RRN не повторяются ни внутри прогона, ни между прогонами на одном стеке.
 */
public final class TestData {

    /** BIN с маршрутом в Switch и записью в bin_issuers: application.yml Switch, миграция V5.2. */
    public static final String KNOWN_BIN = "400000";
    public static final String KNOWN_ISSUER = "ISS001";
    /** BIN, которого нет ни в таблице Switch, ни в bin_issuers. */
    public static final String UNKNOWN_BIN = "999999";

    private static final AtomicInteger SEQUENCE =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(100_000, 900_000));

    private TestData() {
    }

    public static String uniqueStan() {
        return String.format("%06d", SEQUENCE.incrementAndGet() % 1_000_000);
    }

    /** RRN из 12 цифр с префиксом 9: Authorization выдаёт номера с префиксом «год % 10», коллизия исключена до 2029. */
    public static String uniqueRrn() {
        return "9" + String.format("%05d", ThreadLocalRandom.current().nextInt(100_000))
                + String.format("%06d", SEQUENCE.incrementAndGet() % 1_000_000);
    }

    public static String uniqueId() {
        return UUID.randomUUID().toString();
    }

    /** PAN из 16 цифр с известным BIN, которого заведомо нет в CMS: окончание не проходит генерацию через Luhn. */
    public static String nonExistentPan() {
        String body = KNOWN_BIN + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        return body + wrongCheckDigit(body);
    }

    /** CMS маскирует PAN в тексте ошибки до последних четырёх цифр. */
    public static String last4(String pan) {
        return pan.substring(Math.max(0, pan.length() - 4));
    }

    public static String panWithUnknownBin() {
        return UNKNOWN_BIN + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }

    private static int wrongCheckDigit(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int digit = body.charAt(body.length() - 1 - i) - '0';
            int doubled = i % 2 == 0 ? digit * 2 : digit;
            sum += doubled > 9 ? doubled - 9 : doubled;
        }
        int correct = (10 - sum % 10) % 10;
        return (correct + 1) % 10;
    }
}
