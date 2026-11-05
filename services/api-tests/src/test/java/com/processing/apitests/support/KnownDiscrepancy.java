package com.processing.apitests.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Тест фиксирует наблюдаемое поведение, расходящееся с контрактом.
 * Значение - номер записи в docs/practice-5/api-test-report.md, раздел 4.
 * Если расхождение устранят, тест покраснеет: это сигнал обновить ожидание и закрыть запись.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface KnownDiscrepancy {

    String[] value();
}
