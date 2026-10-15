package com.aalsikke.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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

    /** Needed whatever the mail transport. */
    static final List<String> ALWAYS = List.of(
            "DB_URL", "DB_USERNAME", "DB_PASSWORD",
            "JWT_SECRET", "APP_BASE_URL",
            "MAIL_FROM");

    /** KAN-361: extra variables per mail transport (MAIL_TRANSPORT, default smtp). */
    static final List<String> SMTP = List.of("MAIL_USERNAME", "MAIL_PASSWORD");
    static final List<String> BREVO = List.of("BREVO_API_KEY");

    /** ALWAYS plus what the chosen transport needs. An unknown transport stops startup. */
    static List<String> required(Environment environment) {
        String transport = environment.getProperty("MAIL_TRANSPORT", "smtp").trim().toLowerCase(Locale.ROOT);
        List<String> extra = switch (transport.isEmpty() ? "smtp" : transport) {
            case "smtp" -> SMTP;
            case "brevo" -> BREVO;
            default -> throw new IllegalStateException(
                    "MAIL_TRANSPORT must be smtp or brevo, but is: " + transport);
        };
        return Stream.concat(ALWAYS.stream(), extra.stream()).toList();
    }

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
        List<String> missing = required(environment).stream()
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