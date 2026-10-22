package com.processing.service;

import com.processing.config.SwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MerchantAcquirerClientTest {

    private static final String MERCHANT_URL = "http://merchant-acquirer";
    private static final String FEE_URL = MERCHANT_URL + "/api/simulator/merchant/fee";
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-10-21T10:15:30Z");

    private MockRestServiceServer server;
    private MerchantAcquirerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SwitchProperties properties = new SwitchProperties(
                "1.0.0", null, null, null, MERCHANT_URL, null, null, null);
        client = new MerchantAcquirerClient(properties, builder.build());
    }

    @Test
    void shouldReturnFeeWhenMerchantAcquirerResponds() {
        // Arrange
        server.expect(requestTo(FEE_URL))
                .andRespond(withSuccess("{\"acquirerFee\":2250.00}", MediaType.APPLICATION_JSON));

        // Act
        BigDecimal fee = fetchFee();

        // Assert
        assertThat(fee).isEqualByComparingTo("2250.00");
    }

    @Test
    void shouldReturnNullWhenResponseBodyIsEmpty() {
        // Arrange
        server.expect(requestTo(FEE_URL)).andRespond(withSuccess());

        // Act
        BigDecimal fee = fetchFee();

        // Assert
        assertThat(fee).isNull();
    }

    @Test
    void shouldReturnNullWhenMerchantAcquirerFails() {
        // Arrange
        server.expect(requestTo(FEE_URL)).andRespond(withServerError());

        // Act
        BigDecimal fee = fetchFee();

        // Assert
        assertThat(fee).isNull();
    }

    private BigDecimal fetchFee() {
        return client.fetchAcquiringFee(
                TRANSMISSION_TIME, "000001", "4000001234567890", "TERM0001", new BigDecimal("150000"));
    }
}
