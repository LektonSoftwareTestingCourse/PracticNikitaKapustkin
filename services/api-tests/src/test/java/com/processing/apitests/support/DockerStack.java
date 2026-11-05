package com.processing.apitests.support;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Управляет контейнерами стека: отказ зависимости иначе через HTTP не воспроизвести.
 * Используется только в сценариях отказа; состояние данных в них меняется по-прежнему запросами к API.
 *
 * <p>Два режима отказа дают разный ответ Gateway, поэтому выбор режима - часть теста:
 * {@link #stop} убирает адрес контейнера из сети, клиент получает NoRouteToHostException и зависает (D-35);
 * {@link #restart} оставляет адрес, порт закрыт на время старта JVM (10-20 секунд) и клиент сразу
 * получает connection refused. Второй режим воспроизводим, на нём проверяются контрактные 503.
 */
public final class DockerStack {

    private static final Path COMPOSE_FILE = locateComposeFile();
    private static final long COMMAND_TIMEOUT_SECONDS = 90;
    private static final Duration RECOVERY_LIMIT = Duration.ofSeconds(180);

    private DockerStack() {
    }

    public static void stop(String service) {
        compose("stop", service);
    }

    public static void restart(String service) {
        compose("restart", service);
    }

    public static void start(String service) {
        compose("start", service);
    }

    /** Возвращает сервис и ждёт, пока Gateway снова пропускает трафик: circuit breaker открыт до 10 секунд. */
    public static void restore(String service) {
        start(service);
        await().atMost(RECOVERY_LIMIT).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
            .untilAsserted(() -> given().spec(ApiSpecs.gateway())
                .when().get("/health")
                .then().statusCode(200));
    }

    private static void compose(String action, String service) {
        List<String> command = List.of("docker", "compose", "-f", COMPOSE_FILE.toString(), action, service);
        try {
            Process process = new ProcessBuilder(command).inheritIO().start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Timeout: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Exit code " + process.exitValue() + ": " + command);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + command, e);
        }
    }

    private static Path locateComposeFile() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docker-compose.yaml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("docker-compose.yaml not found");
    }
}
