package com.processing.transactionlogger.integration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

final class LoggerEnvironment {
    final Network network = Network.newNetwork();
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withNetwork(network).withNetworkAliases("postgres");
    final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management")
            .withNetwork(network).withNetworkAliases("rabbit");
    final GenericContainer<?> logger = service("transaction-logger")
            .withEnv("SPRING_AMQP_DESERIALIZATION_TRUST_ALL", "true");

    GenericContainer<?> service(String name) {
        Path jar = Path.of("..", name, "target", name + "-1.0.0.jar").toAbsolutePath().normalize();
        return new GenericContainer<>("eclipse-temurin:21-jre-alpine")
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCommand("java", "-Xmx192m", "-XX:ActiveProcessorCount=2", "-jar", "/app.jar")
                .withNetwork(network).withNetworkAliases(name).withExposedPorts(8080)
                .withEnv("SERVER_PORT", "8080")
                .withEnv("POSTGRES_HOST", "postgres")
                .withEnv("POSTGRES_DB", postgres.getDatabaseName())
                .withEnv("POSTGRES_USER", postgres.getUsername())
                .withEnv("POSTGRES_PASSWORD", postgres.getPassword())
                .withEnv("SPRING_RABBITMQ_HOST", "rabbit")
                .withEnv("SPRING_RABBITMQ_USERNAME", rabbit.getAdminUsername())
                .withEnv("SPRING_RABBITMQ_PASSWORD", rabbit.getAdminPassword())
                .dependsOn(postgres, rabbit)
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));
    }

    JdbcTemplate database() {
        return new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    Connection connect() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(rabbit.getAmqpUrl());
        return factory.newConnection();
    }

    void purge(Channel channel) throws Exception {
        channel.queuePurge("transaction-log");
        channel.queuePurge("transaction-log-dlq");
    }
}
