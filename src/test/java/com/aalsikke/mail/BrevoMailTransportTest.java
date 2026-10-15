package com.aalsikke.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** KAN-361: the JSON sent to Brevo must stay valid whatever the email contains. */
class BrevoMailTransportTest {

    @Test
    void jsonEscapesQuotesBackslashesAndControlCharacters() {
        assertThat(BrevoMailTransport.json("say \"hi\"\\ok\nline2\tend\u0001"))
                .isEqualTo("\"say \\\"hi\\\"\\\\ok\\nline2\\tend\\u0001\"");
    }

    @Test
    void bodyHasSenderRecipientSubjectAndBothContents() {
        String body = BrevoMailTransport.body("app@example.com", "user@example.com",
                "Verify your Aal Sikke email", "plain", "<p class=\"x\">html</p>");

        assertThat(body)
                .startsWith("{")
                .endsWith("}")
                .contains("\"sender\":{\"name\":\"Aal Sikke\",\"email\":\"app@example.com\"}")
                .contains("\"to\":[{\"email\":\"user@example.com\"}]")
                .contains("\"subject\":\"Verify your Aal Sikke email\"")
                .contains("\"textContent\":\"plain\"")
                .contains("\"htmlContent\":\"<p class=\\\"x\\\">html</p>\"");
    }
}