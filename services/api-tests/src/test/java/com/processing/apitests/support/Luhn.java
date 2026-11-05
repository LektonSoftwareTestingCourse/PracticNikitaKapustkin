package com.processing.apitests.support;

/** Проверка контрольной цифры PAN: контракт Card, поле pan (docs/api/openapi.yaml). */
public final class Luhn {

    private Luhn() {
    }

    public static boolean isValid(String number) {
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            int digit = number.charAt(number.length() - 1 - i) - '0';
            int value = i % 2 == 1 ? digit * 2 : digit;
            sum += value > 9 ? value - 9 : value;
        }
        return sum % 10 == 0;
    }
}
