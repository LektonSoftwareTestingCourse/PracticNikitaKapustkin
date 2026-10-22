package com.processing.cardmanagement.services.retries;

import com.processing.cardmanagement.exceptions.OutOfRetriesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryServiceImplTest {

    private static final IllegalStateException COLLISION = new IllegalStateException("pan collision");

    @Mock
    private Supplier<String> supplier;

    private final RetryServiceImpl retryService = new RetryServiceImpl();

    @Test
    void shouldReturnValueWhenFirstAttemptSucceeds() {
        // Arrange
        when(supplier.get()).thenReturn("card");

        // Act
        String result = retryService.supply(0, supplier);

        // Assert
        assertThat(result).isEqualTo("card");
    }

    @ParameterizedTest(name = "maxRetries={0}")
    @ValueSource(ints = {2, 3})
    void shouldReturnValueWhenFailuresDoNotExceedMaxRetries(int maxRetries) {
        // Arrange
        when(supplier.get()).thenThrow(COLLISION).thenThrow(COLLISION).thenReturn("card");

        // Act
        String result = retryService.supply(maxRetries, supplier);

        // Assert
        assertThat(result).isEqualTo("card");
    }

    @ParameterizedTest(name = "maxRetries={0}")
    @ValueSource(ints = {0, 1})
    void shouldThrowOutOfRetriesWhenFailuresExceedMaxRetries(int maxRetries) {
        // Arrange
        when(supplier.get()).thenThrow(COLLISION).thenThrow(COLLISION).thenReturn("card");

        // Act + Assert
        assertThatThrownBy(() -> retryService.supply(maxRetries, supplier))
                .isInstanceOf(OutOfRetriesException.class)
                .hasFieldOrPropertyWithValue("reason", COLLISION);
    }
}
