package com.processing.cardmanagement.repositories;

import com.processing.cardmanagement.exceptions.PanCollisionException;
import com.processing.cardmanagement.mappers.CardExpiryDateMapper;
import com.processing.cardmanagement.mappers.CardPersistenceMapperImpl;
import com.processing.cardmanagement.models.Card;
import com.processing.cardmanagement.models.CardStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CardRepositoryPersistenceAdapter.class, CardPersistenceMapperImpl.class, CardExpiryDateMapper.class})
class CardRepositoryIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private final String pan = "400000%010d".formatted(ThreadLocalRandom.current().nextLong(10_000_000_000L));
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CardRepository repository;

    @BeforeEach
    void clearCards() {
        jdbc.execute("TRUNCATE cards RESTART IDENTITY CASCADE");
    }

    @Test
    void preservesAllCardFieldsAfterCommit() {
        Card card = newCard();
        repository.create(card);
        assertThat(repository.findByPan(card.pan())).contains(card);
    }

    @Test
    void rejectsSecondActiveCard() {
        Card first = repository.create(newCard());
        Card duplicate = first.copyWithPan(first.pan());
        assertThatThrownBy(() -> repository.create(duplicate)).isInstanceOf(PanCollisionException.class);
        assertThat(repository.findByPan(first.pan())).contains(first);
    }

    @Test
    void reusesPanAfterSoftDelete() {
        Card first = repository.create(newCard());
        repository.update(first.deleted());
        Card replacement = newCard();
        repository.create(replacement);
        assertThat(repository.findByPan(first.pan())).contains(replacement);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cards WHERE pan = ?", Integer.class, first.pan())).isEqualTo(2);
    }

    private Card newCard() {
        return new Card(UUID.randomUUID(), pan, "400000", "IVAN IVANOV", YearMonth.of(2030, 12),
                CardStatus.ACTIVE, "643", BigDecimal.valueOf(1500), BigDecimal.valueOf(4500),
                BigDecimal.valueOf(7000), "ISS001", Instant.parse("2026-04-12T10:15:00Z"));
    }
}
