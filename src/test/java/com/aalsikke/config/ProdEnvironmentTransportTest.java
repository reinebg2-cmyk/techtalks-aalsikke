package com.aalsikke.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KAN-361: which variables are required depends on MAIL_TRANSPORT. */
class ProdEnvironmentTransportTest {

    private static MockEnvironment baseVariablesSet() {
        MockEnvironment environment = new MockEnvironment();
        ProdEnvironmentCheck.ALWAYS.forEach(name -> environment.setProperty(name, "secret-" + name));
        return environment;
    }

    @Test
    void brevoNeedsTheApiKeyInsteadOfSmtpCredentials() {
        MockEnvironment environment = baseVariablesSet().withProperty("MAIL_TRANSPORT", "brevo");

        assertThatThrownBy(() -> ProdEnvironmentCheck.check(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BREVO_API_KEY")
                .hasMessageNotContaining("MAIL_USERNAME")
                .hasMessageNotContaining("MAIL_PASSWORD");
    }

    @Test
    void brevoWithApiKeyPassesWithoutSmtpCredentials() {
        MockEnvironment environment = baseVariablesSet()
                .withProperty("MAIL_TRANSPORT", "brevo")
                .withProperty("BREVO_API_KEY", "secret-key");

        assertThatCode(() -> ProdEnvironmentCheck.check(environment)).doesNotThrowAnyException();
    }

    @Test
    void unknownTransportStopsStartup() {
        MockEnvironment environment = baseVariablesSet().withProperty("MAIL_TRANSPORT", "brevoo");

        assertThatThrownBy(() -> ProdEnvironmentCheck.check(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_TRANSPORT must be smtp or brevo");
    }
}