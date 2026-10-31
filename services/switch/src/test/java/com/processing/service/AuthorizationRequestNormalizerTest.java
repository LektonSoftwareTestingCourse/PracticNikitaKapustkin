package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {

    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-10-21T10:15:30Z");

    @ParameterizedTest(name = "terminalId \"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            "TERM001,   TERM0010",
            "TERM0001,  TERM0001",
            "TERM00012, TERM0001",
            "T1,        T1000000",
            "'',        ''",
            "'   ',     '   '",
            "NULL,      NULL"
    }, nullValues = "NULL")
    void shouldBringTerminalIdToEightCharactersWhenNormalizing(String terminalId, String expected) {
        // Act
        String normalized = AuthorizationRequestNormalizer.normalizeTerminalId(terminalId);

        // Assert
        assertThat(normalized).isEqualTo(expected);
    }

    @ParameterizedTest(name = "merchantId \"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            "MERCH0000000001,   MERCH0000000001",
            "MERCH00000000001,  MERCH0000000001",
            "MERCH12345678901,  MERCH1234567890",
            "ABCDE00000000001,  ABCDE0000000000",
            "MERCH000000000012, MERCH0000000000",
            "MERCH000000001,    MERCH0000000010",
            "'',                ''",
            "NULL,              NULL"
    }, nullValues = "NULL")
    void shouldBringMerchantIdToFifteenCharactersWhenNormalizing(String merchantId, String expected) {
        // Act
        String normalized = AuthorizationRequestNormalizer.normalizeMerchantId(merchantId);

        // Assert
        assertThat(normalized).isEqualTo(expected);
    }

    @Test
    void shouldNormalizeOnlyTerminalAndMerchantIdsWhenNormalizingRequest() {
        // Arrange
        AuthorizationRequest request = requestWithIds("T1", "MERCH000000001");

        // Act
        AuthorizationRequest normalized = AuthorizationRequestNormalizer.normalize(request);

        // Assert
        assertThat(normalized)
                .usingRecursiveComparison()
                .ignoringFields("terminalId", "merchantId")
                .isEqualTo(request);
        assertThat(normalized.terminalId()).isEqualTo("T1000000");
        assertThat(normalized.merchantId()).isEqualTo("MERCH0000000010");
    }

    private static AuthorizationRequest requestWithIds(String terminalId, String merchantId) {
        return new AuthorizationRequest(
                "0100", "000001", "4000001234567890", "000000",
                new BigDecimal("150000"), "643", TRANSMISSION_TIME,
                terminalId, "POS", merchantId, "5411", "ACQ001", "ISS001");
    }
}
