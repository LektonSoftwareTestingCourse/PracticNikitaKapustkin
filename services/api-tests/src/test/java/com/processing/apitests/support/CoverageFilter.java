package com.processing.apitests.support;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Пишет порт, метод, шаблон пути и статус каждого запроса: из этого журнала строится таблица покрытия отчёта. */
final class CoverageFilter implements Filter {

    private static final Path JOURNAL = Path.of("target", "api-coverage.log");

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response,
                           FilterContext context) {
        Response result = context.next(request, response);
        String line = String.join(" ", String.valueOf(request.getPort()), request.getMethod(),
                request.getUserDefinedPath(), String.valueOf(result.statusCode())) + System.lineSeparator();
        append(line);
        return result;
    }

    private static synchronized void append(String line) {
        try {
            Files.writeString(JOURNAL, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
