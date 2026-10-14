package com.aalsikke.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KAN-358: the prod profile must refuse to start when a required variable is missing. */
class ProdEnvironmentCheckTest {

    @Test
    void passesWhenEveryRequiredVariableIsSet() {
        MockEnvironment environment = new MockEnvironment();
        ProdEnvironmentCheck.REQUIRED.forEach(name -> environment.setProperty(name, "secret-" + name));

        assertThatCode(() -> ProdEnvironmentCheck.check(environment)).doesNotThrowAnyException();
    }

    @Test
    void listsMissingAndBlankVariablesByNameWithoutTheirValues() {
        MockEnvironment environment = new MockEnvironment();
        ProdEnvironmentCheck.REQUIRED.stream()
                .filter(name -> !name.equals("DB_PASSWORD"))   // missing entirely
                .forEach(name -> environment.setProperty(name, "secret-" + name));
        environment.setProperty("MAIL_PASSWORD", "   ");       // set but blank

        assertThatThrownBy(() -> ProdEnvironmentCheck.check(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD, MAIL_PASSWORD")
                .hasMessageNotContaining("secret-");
    }
}