package com.processing.cardmanagement.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    private final LuhnValidator validator = new LuhnValidator();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"4111111111111111", "5500000000000004", "4000000000000002", "0000000000000000"})
    void shouldAcceptPanWhenLuhnChecksumIsValid(String pan) {
        // Act
        boolean valid = validator.isValid(pan);

        // Assert
        assertThat(valid).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"4111111111111112", "5500000000000005", "4000000000000001"})
    void shouldRejectPanWhenLuhnChecksumIsInvalid(String pan) {
        // Act
        boolean valid = validator.isValid(pan);

        // Assert
        assertThat(valid).isFalse();
    }

    @Test
    void shouldGenerateLuhnValidSixteenDigitPanWhenBinIsGiven() {
        // Act
        String pan = validator.generatePan("400000");

        // Assert
        assertThat(pan).hasSize(16).startsWith("400000").containsOnlyDigits();
        assertThat(validator.isValid(pan)).isTrue();
    }
}
