package com.processing.authorization.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LimitUsageRepositoryIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private final String pan = "400000%010d".formatted(ThreadLocalRandom.current().nextLong(10_000_000_000L));
    @Autowired
    JdbcTemplate jdbc;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 12);

    @Autowired
    LimitUsageRepository repository;
    @Autowired
    TransactionTemplate transaction;

    @BeforeEach
    void clearUsage() {
        jdbc.execute("TRUNCATE limit_usage RESTART IDENTITY CASCADE");
    }

    @Test
    void insertsFirstUsage() {
        assertThat(upsert(60)).isOne();
        assertAmounts(60);
    }

    @Test
    void accumulatesOnSameKeyUpToDailyLimit() {
        upsert(60);
        assertThat(upsert(40)).isOne();
        assertAmounts(100);
    }

    @Test
    void rejectsOneOverDailyLimitWithoutChangingUsage() {
        upsert(60);
        assertThat(upsert(41)).isZero();
        assertAmounts(60);
    }

    @Test
    void rejectsNewDayWhenMonthlyLimitIsExhausted() {
        for (int day = 0; day < 5; day++) {
            assertThat(upsert(DATE.plusDays(day), 100)).isOne();
        }
        assertThat(upsert(DATE.plusDays(5), 1)).isZero();
        assertThat(repository.count()).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT monthly_amount FROM limit_usage WHERE pan = ? AND usage_date = ?",
                BigDecimal.class, pan, DATE.plusDays(4))).isEqualByComparingTo("500");
    }

    private int upsert(long amount) {
        return upsert(DATE, amount);
    }

    private int upsert(LocalDate date, long amount) {
        return transaction.execute(status -> repository.upsertLimitUsage(pan, date, BigDecimal.valueOf(amount),
                BigDecimal.valueOf(100), BigDecimal.valueOf(500)));
    }

    private void assertAmounts(long amount) {
        assertThat(repository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getPan()).isEqualTo(pan);
            assertThat(row.getUsageDate()).isEqualTo(DATE);
            assertThat(row.getDailyAmount()).isEqualByComparingTo(BigDecimal.valueOf(amount));
            assertThat(row.getMonthlyAmount()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        });
    }
}
