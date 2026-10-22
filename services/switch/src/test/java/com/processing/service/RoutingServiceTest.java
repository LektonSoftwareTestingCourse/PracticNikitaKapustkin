package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {

    private static final Map<String, String> BIN_ROUTING = Map.of(
            "400000", "ISS001",
            "400001", "ISS002",
            "400002", "ISS003",
            "400003", "ISS004",
            "400004", "ISS005");

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(propertiesWithBinRouting(BIN_ROUTING));
    }

    @ParameterizedTest(name = "PAN {0} -> {1}")
    @CsvSource({
            "4000001234567890, ISS001",
            "4000011234567890, ISS002",
            "4000021234567890, ISS003",
            "4000031234567890, ISS004",
            "4000041234567890, ISS005",
            "400000,           ISS001"
    })
    void shouldReturnIssuerIdWhenBinIsInRoutingTable(String pan, String expectedIssuerId) {
        // Act
        String issuerId = routingService.getIssuerIdByPan(pan);

        // Assert
        assertThat(issuerId).isEqualTo(expectedIssuerId);
    }

    @Test
    void shouldThrowUnknownBinWhenBinIsNotInRoutingTable() {
        // Act + Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan("4999991234567890"))
                .isInstanceOf(UnknownBinException.class)
                .hasMessageContaining("499999");
    }

    @ParameterizedTest(name = "pan=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "40000"})
    void shouldThrowUnknownBinWhenPanIsNullOrShorterThanBin(String pan) {
        // Act + Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan(pan))
                .isInstanceOf(UnknownBinException.class);
    }

    private static SwitchProperties propertiesWithBinRouting(Map<String, String> binRouting) {
        return new SwitchProperties("1.0.0", binRouting, null, null, null, null, null, null);
    }
}
