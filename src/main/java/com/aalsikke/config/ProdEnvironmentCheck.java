package com.aalsikke.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * KAN-358: in production, every required environment variable must be set.
 *
 * Values read with @Value fail at startup when a placeholder can't be resolved, but
 * Spring Boot's own settings (spring.datasource.*, spring.mail.*) keep an unresolved
 * "${DB_PASSWORD}" as literal text. Without this check, a missing MAIL_PASSWORD would
 * let the app start and every email would fail silently.
 *
 * Runs as a BeanFactoryPostProcessor, so it checks before any bean is created:
 * before the database connection, Flyway and the web server.
 */
@Component
@Profile("prod")
public class ProdEnvironmentCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    static final List<String> REQUIRED = List.of(
            "DB_URL", "DB_USERNAME", "DB_PASSWORD",
            "JWT_SECRET", "APP_BASE_URL",
            "MAIL_USERNAME", "MAIL_PASSWORD", "MAIL_FROM");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        check(environment);
    }

    /** Throws if any required variable is missing or blank. Names only, never values. */
    static void check(Environment environment) {
        List<String> missing = REQUIRED.stream()
                .filter(name -> {
                    String value = environment.getProperty(name);
                    return value == null || value.isBlank();
                })
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variables for the prod profile: "
                            + String.join(", ", missing));
        }
    }
}