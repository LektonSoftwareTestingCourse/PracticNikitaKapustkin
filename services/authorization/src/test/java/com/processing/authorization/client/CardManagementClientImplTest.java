package com.processing.authorization.client;

import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.GetCardException;
import com.processing.authorization.exceptions.InsufficientFundsException;
import com.processing.authorization.exceptions.InternalCardManagerException;
import com.processing.authorization.exceptions.InvalidGetCardRequestException;
import com.processing.authorization.exceptions.InvalidReserveRequestException;
import com.processing.authorization.exceptions.InvalidRollbackRequestException;
import com.processing.authorization.exceptions.PaymentRequiredException;
import com.processing.authorization.exceptions.ReserveException;
import com.processing.authorization.exceptions.RollbackConflictException;
import com.processing.authorization.exceptions.RollbackFailureException;
import com.processing.authorization.exceptions.ServiceUnavailableException;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class CardManagementClientImplTest {

    private static final String CMS_URL = "http://card-management";
    private static final String PAN = "4000001234567890";
    private static final String RRN = "612345678901";
    private static final String CARD_JSON = """
            {"id":"00000000-0000-0000-0000-000000000001","pan":"4000001234567890","bin":"400000",
             "cardholderName":"IVAN IVANOV","expiryDate":"2029-10","status":"ACTIVE","currencyCode":"643",
             "dailyLimit":15000000,"monthlyLimit":300000000,"availableBalance":1000000,"issuerId":"ISS001",
             "createdAt":"2026-01-01T00:00:00Z"}
            """;

    @Mock
    private AuthorizationEventNotifier eventNotifier;

    private MockRestServiceServer server;
    private CardManagementClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CardManagementClientImpl(builder.build(), eventNotifier);
        ReflectionTestUtils.setField(client, "cmsUrl", CMS_URL);
    }

    @Test
    void shouldReturnCardWhenCardManagementFindsIt() {
        // Arrange
        server.expect(requestTo(CMS_URL + "/api/cards/" + PAN))
                .andRespond(withSuccess(CARD_JSON, MediaType.APPLICATION_JSON));

        // Act
        CardModel card = client.getCard(PAN);

        // Assert
        assertThat(card.pan()).isEqualTo(PAN);
        assertThat(card.status()).isEqualTo(CardModelStatus.ACTIVE);
        assertThat(card.availableBalance()).isEqualByComparingTo("1000000");
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @MethodSource("getCardErrors")
    void shouldThrowMappedExceptionWhenGetCardFails(HttpStatus status, Class<? extends Exception> expected) {
        // Arrange
        server.expect(requestTo(CMS_URL + "/api/cards/" + PAN)).andRespond(withStatus(status));

        // Act + Assert
        assertThatThrownBy(() -> client.getCard(PAN)).isInstanceOf(expected);
    }

    @Test
    void shouldSendAmountAndRrnWhenReservingFunds() {
        // Arrange
        server.expect(requestTo(CMS_URL + "/api/cards/" + PAN + "/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"amount\":150000,\"rrn\":\"612345678901\"}"))
                .andRespond(withSuccess());

        // Act
        client.reserve(new BigDecimal("150000"), RRN, PAN);

        // Assert
        server.verify();
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @MethodSource("reserveErrors")
    void shouldThrowMappedExceptionWhenReserveFails(HttpStatus status, Class<? extends Exception> expected) {
        // Arrange
        server.expect(requestTo(CMS_URL + "/api/cards/" + PAN + "/reserve")).andRespond(withStatus(status));

        // Act + Assert
        assertThatThrownBy(() -> client.reserve(new BigDecimal("150000"), RRN, PAN)).isInstanceOf(expected);
    }

    @Test
    void shouldPostRequestToCardManagementWhenRollingBack() {
        // Arrange
        server.expect(requestTo(CMS_URL + "/api/cards/" + PAN + "/rollback"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"rrn\":\"612345678901\",\"pan\":\"4000001234567890\"}"))
                .andRespond(withSuccess());

        // Act
        client.rollback(rollbackRequest());

        // Assert
        server.verify();
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @MethodSource("rollbackErrors")
    void shouldThrowMappedExceptionWhenRollbackFails(HttpStatus status, Class<? extends Exception> expected) {
        // Arrange
        server.expect(requestTo(CMS_URL + "/api/cards/" + PAN + "/rollback")).andRespond(withStatus(status));

        // Act + Assert
        assertThatThrownBy(() -> client.rollback(rollbackRequest())).isInstanceOf(expected);
    }

    static Stream<Arguments> getCardErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, InternalCardManagerException.class),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, ServiceUnavailableException.class),
                Arguments.of(HttpStatus.BAD_REQUEST, InvalidGetCardRequestException.class),
                Arguments.of(HttpStatus.PAYMENT_REQUIRED, PaymentRequiredException.class),
                Arguments.of(HttpStatus.NOT_FOUND, CardNotFoundException.class),
                Arguments.of(HttpStatus.CONFLICT, GetCardException.class));
    }

    static Stream<Arguments> reserveErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, InternalCardManagerException.class),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, ServiceUnavailableException.class),
                Arguments.of(HttpStatus.BAD_REQUEST, InvalidReserveRequestException.class),
                Arguments.of(HttpStatus.PAYMENT_REQUIRED, InsufficientFundsException.class),
                Arguments.of(HttpStatus.NOT_FOUND, CardNotFoundException.class),
                Arguments.of(HttpStatus.CONFLICT, ReserveException.class));
    }

    static Stream<Arguments> rollbackErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, InternalCardManagerException.class),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, ServiceUnavailableException.class),
                Arguments.of(HttpStatus.BAD_REQUEST, InvalidRollbackRequestException.class),
                Arguments.of(HttpStatus.NOT_FOUND, CardNotFoundException.class),
                Arguments.of(HttpStatus.CONFLICT, RollbackConflictException.class),
                Arguments.of(HttpStatus.UNPROCESSABLE_ENTITY, RollbackFailureException.class));
    }

    private static RollbackRequest rollbackRequest() {
        return new RollbackRequest(RRN, PAN, new BigDecimal("150000"));
    }
}
