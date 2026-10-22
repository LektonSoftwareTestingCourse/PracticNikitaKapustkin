package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestValidatorTest {

    private static final String PAN = "4000001234567890";
    private static final BigDecimal AMOUNT = new BigDecimal("150000");
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-10-21T10:15:30Z");

    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    @Test
    void shouldAcceptRequestWhenAllFieldsAreValid() {
        // Act + Assert
        assertThatCode(() -> validator.validate(validRequest())).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRequestWhenBodyIsMissing() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Request body is required");
    }

    @ParameterizedTest(name = "missing {1}")
    @MethodSource("requestsWithMissingField")
    void shouldRejectRequestWhenRequiredFieldIsMissing(AuthorizationRequest request, String field) {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field '" + field + "' is required");
    }

    @ParameterizedTest(name = "mti=''{0}''")
    @ValueSource(strings = {"0200", "0400", "100", "01000"})
    void shouldRejectRequestWhenMtiIsNotAuthorizationRequest(String mti) {
        // Arrange
        AuthorizationRequest request = request(mti, PAN, AMOUNT, "643", "5411");

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("mti");
    }

    @ParameterizedTest(name = "pan=''{0}''")
    @ValueSource(strings = {"400000123456789", "40000012345678901", "400000123456789A", "4000 00123456789"})
    void shouldRejectPanWhenItIsNotSixteenDigits(String pan) {
        // Arrange
        AuthorizationRequest request = request("0100", pan, AMOUNT, "643", "5411");

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'pan' must be exactly 16 digits");
    }

    @ParameterizedTest(name = "amount={0}")
    @CsvSource(value = {"NULL, Field 'amount' is required", "0, Field 'amount' must be > 0",
            "-1, Field 'amount' must be > 0"}, nullValues = "NULL")
    void shouldRejectAmountWhenItIsMissingOrNotPositive(BigDecimal amount, String message) {
        // Arrange
        AuthorizationRequest request = request("0100", PAN, amount, "643", "5411");

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(message);
    }

    @ParameterizedTest(name = "amount={0}")
    @ValueSource(strings = {"0.01", "1", "999999999"})
    void shouldAcceptRequestWhenAmountIsPositive(BigDecimal amount) {
        // Arrange
        AuthorizationRequest request = request("0100", PAN, amount, "643", "5411");

        // Act + Assert
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "currency=''{0}''")
    @ValueSource(strings = {"64", "6430"})
    void shouldRejectCurrencyCodeWhenItIsNotThreeCharacters(String currencyCode) {
        // Arrange
        AuthorizationRequest request = request("0100", PAN, AMOUNT, currencyCode, "5411");

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("currencyCode");
    }

    @ParameterizedTest(name = "mcc=''{0}''")
    @ValueSource(strings = {"541", "54110", "54A1"})
    void shouldRejectMccWhenItIsNotFourDigits(String mcc) {
        // Arrange
        AuthorizationRequest request = request("0100", PAN, AMOUNT, "643", mcc);

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'mcc' must be exactly 4 digits");
    }

    @ParameterizedTest(name = "pan=''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReportPanAsMissingWhenPanIsBlank(String pan) {
        // Arrange
        AuthorizationRequest request = request("0100", pan, AMOUNT, "643", "5411");

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'pan' is required");
    }

    static Stream<Arguments> requestsWithMissingField() {
        return Stream.of(
                Arguments.of(new AuthorizationRequest(null, "000001", PAN, "000000", AMOUNT, "643",
                        TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null), "mti"),
                Arguments.of(new AuthorizationRequest("0100", "", PAN, "000000", AMOUNT, "643",
                        TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null), "stan"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, " ", AMOUNT, "643",
                        TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null),
                        "processingCode"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, "000000", AMOUNT, null,
                        TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null),
                        "currencyCode"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, "000000", AMOUNT, "643",
                        null, "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null),
                        "transmissionDateTime"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, "000000", AMOUNT, "643",
                        TRANSMISSION_TIME, null, "POS", "MERCH0000000001", "5411", "ACQ001", null), "terminalId"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, "000000", AMOUNT, "643",
                        TRANSMISSION_TIME, "TERM0001", "POS", null, "5411", "ACQ001", null), "merchantId"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, "000000", AMOUNT, "643",
                        TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", null, "ACQ001", null), "mcc"),
                Arguments.of(new AuthorizationRequest("0100", "000001", PAN, "000000", AMOUNT, "643",
                        TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", "5411", null, null),
                        "acquirerId"));
    }

    private static AuthorizationRequest validRequest() {
        return request("0100", PAN, AMOUNT, "643", "5411");
    }

    private static AuthorizationRequest request(String mti, String pan, BigDecimal amount,
                                                String currencyCode, String mcc) {
        return new AuthorizationRequest(mti, "000001", pan, "000000", amount, currencyCode, TRANSMISSION_TIME,
                "TERM0001", "POS", "MERCH0000000001", mcc, "ACQ001", null);
    }
}
