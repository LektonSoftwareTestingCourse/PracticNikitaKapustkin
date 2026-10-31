package com.processing.gateway.logging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogDataMaskerTest {

    private final LogDataMasker masker = new LogDataMasker();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "{\"pan\":\"4000001234567890\"}                | {\"pan\":\"400000****7890\"}",
            "{\"PAN\" : \"4000001234567890\"}              | {\"pan\":\"400000****7890\"}",
            "{\"pan\":\"4000001234567890123\"}             | {\"pan\":\"400000****0123\"}",
            "{\"cvv\":\"123\"}                             | {\"cvv\":\"***\"}",
            "{\"cvv\":\"1234\"}                            | {\"cvv\":\"***\"}",
            "{\"pan\":\"4000001234567890\",\"cvv\":\"123\"} | {\"pan\":\"400000****7890\",\"cvv\":\"***\"}"
    })
    void shouldMaskValueWhenJsonContainsPanOrCvv(String body, String expected) {
        // Act
        String masked = masker.maskData(body);

        // Assert
        assertThat(masked).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "{\"pan\":\"400000123456789\"}",
            "{\"pan\":\"40000012345678901234\"}",
            "{\"stan\":\"000001\",\"amount\":150000}",
            "pan=4000001234567890"
    })
    void shouldLeaveDataUnchangedWhenNothingMatchesMaskPatterns(String body) {
        // Act
        String masked = masker.maskData(body);

        // Assert
        assertThat(masked).isEqualTo(body);
    }
}
