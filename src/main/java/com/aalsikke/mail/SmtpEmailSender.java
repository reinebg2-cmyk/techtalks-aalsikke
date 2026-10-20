package com.aalsikke.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * S5-02: sends the app's real emails (verification, reminders, security alerts, password reset).
 * Active only when app.mail.enabled=true (env MAIL_ENABLED); otherwise LoggingEmailSender is used.
 * KAN-361: this class builds the content; the active MailTransport delivers it
 * (SMTP by default, Brevo's HTTPS API with MAIL_TRANSPORT=brevo).
 * A sending failure is logged and swallowed, so registration still succeeds and the user can press Resend.
 * Reminders are the exception: their failure is thrown so the reminder job retries.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    /** KAN-336: reminder times are shown in Lebanon time (the DB stores UTC). */
    private static final ZoneId APP_ZONE = com.aalsikke.config.AppTime.ZONE;
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN_AR =
            DateTimeFormatter.ofPattern("EEE d MMM. HH:mm", Locale.forLanguageTag("ar-LB-u-nu-latn"));

    private final MailTransport transport;
    private final String baseUrl;

    public SmtpEmailSender(MailTransport transport,
                           @Value("${app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        try {
            transport.send(to, "Verify your Aal Sikke email", plainText(verificationLink), html(verificationLink));
            log.info("[EMAIL] Verification email sent to {}", to);
        } catch (MailException e) {
            // Don't fail registration or resend: the user can request a new link later.
            log.error("[EMAIL] Could not send verification email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendReminderEmail(String to, String name, String taskTitle, OffsetDateTime dueAt) {
        // Thrown on failure (MailException is not caught): the reminder job retries next minute.
        String title = taskTitle.replaceAll("[\\r\\n]+", " ");   // keep the subject on one line
        String due = dueAt == null ? null : WHEN.format(dueAt.atZoneSameInstant(APP_ZONE));
        transport.send(to, "Reminder: " + title, reminderText(name, title, due), reminderHtml(name, title, due));
        log.info("[EMAIL] Reminder email sent to {}", to);
    }
    @Override
    public void sendReminderEmail(String to, String name, String taskTitle, OffsetDateTime dueAt, String lang) {
        if (!"ar".equals(lang)) {
            sendReminderEmail(to, name, taskTitle, dueAt);
            return;
        }
        // Thrown on failure (MailException is not caught): the reminder job retries next minute.
        String title = taskTitle.replaceAll("[\\r\\n]+", " ");
        String due = dueAt == null ? null : WHEN_AR.format(dueAt.atZoneSameInstant(APP_ZONE));
        transport.send(to, "تذكير: " + title, reminderTextAr(name, title, due), reminderHtmlAr(name, title, due));
        log.info("[EMAIL] Arabic reminder email sent to {}", to);
    }

    private String reminderTextAr(String name, String title, String due) {
        String dueLine = due == null ? "" : "الموعد: " + due + "\n";
        return """
            مرحبًا %s،

            تذكير بمهمتك: «%s»
            %s
            افتح عالسكة: %s/dashboard.html
            """.formatted(name, title, dueLine, baseUrl);
    }

    private String reminderHtmlAr(String name, String title, String due) {
        String dueHtml = due == null ? "" : "<p>الموعد: " + HtmlUtils.htmlEscape(due) + "</p>";
        return """
            <div dir="rtl" style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937;text-align:right">
              <h2 style="color:#2C6E9E">تذكير بمهمتك</h2>
              <p>مرحبًا %s،</p>
              <p><strong>%s</strong></p>
              %s
              <p style="margin:24px 0">
                <a href="%s/dashboard.html" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">افتح عالسكة</a>
              </p>
            </div>
            """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(title),
                dueHtml, HtmlUtils.htmlEscape(baseUrl));
    }

    @Override
    public void sendSecurityAlert(String to, String name, String title, String detail) {
        try {
            String when = WHEN.format(OffsetDateTime.now().atZoneSameInstant(APP_ZONE));
            transport.send(to, "Security alert: " + title,
                    securityText(name, title, detail, when), securityHtml(name, title, detail, when));
            log.info("[EMAIL] Security alert sent to {}", to);
        } catch (MailException e) {
            // KAN-337: the change already happened; a failed alert must not undo or block it.
            log.error("[EMAIL] Could not send security alert to {}: {}", to, e.getMessage());
        }
    }
    @Override
    public void sendWelcomeEmail(String to, String name, String lang) {
        if (!"ar".equals(lang)) {
            sendWelcomeEmail(to, name);
            return;
        }
        try {
            transport.send(to, "أهلًا بك في عالسكة، حسابك جاهز", welcomeTextAr(name), welcomeHtmlAr(name));
            log.info("[EMAIL] Arabic welcome email sent to {}", to);
        } catch (MailException e) {
            log.error("[EMAIL] Could not send welcome email to {}: {}", to, e.getMessage());
        }
    }

    private String welcomeTextAr(String name) {
        return """
            مرحبًا %s،

            أهلًا بك في عالسكة! حسابك جاهز.

            بعض الطرق لتبدأ بشكل جيد:
            - أضف محاضراتك ودوامك ومواعيدك النهائية، واضبط التكرار الأسبوعي.
            - استخدم «اقترح وقتًا» لإيجاد وقت فارغ حول بقية جدولك.
            - راجع مراجعتك الأسبوعية لترى ما أنجزته.

            افتح عالسكة: %s/dashboard.html
            """.formatted(name, baseUrl);
    }

    private String welcomeHtmlAr(String name) {
        return """
            <div dir="rtl" style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937;text-align:right">
              <h2 style="color:#2C6E9E">أهلًا بك في عالسكة!</h2>
              <p>مرحبًا %s، حسابك جاهز.</p>
              <p>بعض الطرق لتبدأ بشكل جيد:</p>
              <ul style="padding-right:20px;color:#374151">
                <li>أضف محاضراتك ودوامك ومواعيدك النهائية، واضبط التكرار الأسبوعي.</li>
                <li>استخدم <strong>اقترح وقتًا</strong> لإيجاد وقت فارغ حول بقية جدولك.</li>
                <li>راجع <strong>مراجعتك الأسبوعية</strong> لترى ما أنجزته.</li>
              </ul>
              <p style="margin:24px 0">
                <a href="%s/dashboard.html" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">افتح عالسكة</a>
              </p>
            </div>
            """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(baseUrl));
    }

    private String securityText(String name, String title, String detail, String when) {
        return """
                Hi %s,

                %s
                %s
                When: %s (Beirut time)

                If this was you, you can ignore this email.
                If it wasn't, change your password right away: %s/settings.html
                """.formatted(name, title, detail, when, baseUrl);
    }

    private String securityHtml(String name, String title, String detail, String when) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#b42318">Security alert</h2>
                  <p>Hi %s,</p>
                  <div style="border-left:4px solid #b42318;padding:8px 12px;background:#fdf3f2;border-radius:4px">
                    <p style="margin:0;font-size:16px"><strong>%s</strong></p>
                    <p style="margin:4px 0 0;color:#6b7280">%s</p>
                    <p style="margin:4px 0 0;color:#6b7280">When: %s (Beirut time)</p>
                  </div>
                  <p>If this was you, you can ignore this email.</p>
                  <p style="margin:24px 0">
                    <a href="%s/settings.html" style="background:#b42318;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">This wasn't me</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">The button opens Settings, where you can change your password.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(detail),
                HtmlUtils.htmlEscape(when), HtmlUtils.htmlEscape(baseUrl));
    }

    private String reminderText(String name, String title, String due) {
        return """
                Hi %s,

                This is your reminder for: %s
                %s
                Open Aal Sikke: %s/dashboard.html
                """.formatted(name, title, due == null ? "" : "Due: " + due + "\n", baseUrl);
    }

    private String reminderHtml(String name, String title, String due) {
        String dueLine = due == null ? ""
                : "<p style=\"margin:4px 0 0;color:#6b7280\">Due: <strong>" + HtmlUtils.htmlEscape(due) + "</strong></p>";
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Reminder</h2>
                  <p>Hi %s,</p>
                  <div style="border-left:4px solid #2C6E9E;padding:8px 12px;background:#f3f6fa;border-radius:4px">
                    <p style="margin:0;font-size:16px"><strong>%s</strong></p>
                    %s
                  </div>
                  <p style="margin:24px 0">
                    <a href="%s/dashboard.html" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Open Aal Sikke</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">You can turn reminder emails off in Settings &rarr; Event alerts.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(title), dueLine,
                HtmlUtils.htmlEscape(baseUrl));
    }

    private static String plainText(String link) {
        return """
                Welcome to Aal Sikke!

                Please confirm your email address by opening this link:
                %s

                The link is valid for 1 hour and works once.
                If you didn't create an account, you can ignore this email.
                """.formatted(link);
    }

    private static String html(String link) {
        String safe = HtmlUtils.htmlEscape(link);
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Welcome to Aal Sikke!</h2>
                  <p>Please confirm your email address to start using your account.</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Verify my email</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">Or copy this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#6b7280">The link is valid for 1 hour and works once.
                  If you didn't create an account, you can ignore this email.</p>
                </div>
                """.formatted(safe, safe);
    }

    @Override
    public void sendWelcomeEmail(String to, String name) {
        try {
            transport.send(to, "Welcome to Aal Sikke, your account is ready", welcomeText(name), welcomeHtml(name));
            log.info("[EMAIL] Welcome email sent to {}", to);
        } catch (MailException e) {
            // KAN-369: the login already succeeded; a failed welcome email must not block it.
            log.error("[EMAIL] Could not send welcome email to {}: {}", to, e.getMessage());
        }
    }

    private String welcomeText(String name) {
        return """
                Hi %s,

                Welcome to Aal Sikke! Your account is ready.

                A few ways to get on track:
                - Add your classes, shifts and deadlines, and set weekly repeats.
                - Use "Suggest a time" to find free time around everything else.
                - Check your weekly review to see what you finished.

                Open Aal Sikke: %s/dashboard.html
                """.formatted(name, baseUrl);
    }

    private String welcomeHtml(String name) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Welcome to Aal Sikke!</h2>
                  <p>Hi %s, your account is ready.</p>
                  <p>A few ways to get on track:</p>
                  <ul style="padding-left:20px;color:#374151">
                    <li>Add your classes, shifts and deadlines, and set weekly repeats.</li>
                    <li>Use <strong>Suggest a time</strong> to find free time around everything else.</li>
                    <li>Check your <strong>weekly review</strong> to see what you finished.</li>
                  </ul>
                  <p style="margin:24px 0">
                    <a href="%s/dashboard.html" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Open Aal Sikke</a>
                  </p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(baseUrl));
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        try {
            transport.send(to, "Reset your Aal Sikke password", resetText(resetLink), resetHtml(resetLink));
            log.info("[EMAIL] Password reset email sent to {}", to);
        } catch (MailException e) {
            // Silent for the user (so the response never reveals whether the account exists).
            log.error("[EMAIL] Could not send password reset email to {}: {}", to, e.getMessage());
        }
    }

    private static String resetText(String link) {
        return """
                We received a request to reset your Aal Sikke password.

                Choose a new password by opening this link:
                %s

                The link is valid for 30 minutes and works once.
                If you didn't ask for this, you can ignore this email. Your password will not change.
                """.formatted(link);
    }

    private static String resetHtml(String link) {
        String safe = HtmlUtils.htmlEscape(link);
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Reset your password</h2>
                  <p>We received a request to reset your Aal Sikke password. Choose a new one with the button below.</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Reset my password</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">Or copy this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#6b7280">The link is valid for 30 minutes and works once.
                  If you didn't ask for this, you can ignore this email. Your password will not change.</p>
                </div>
                """.formatted(safe, safe);
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink, String lang) {
        if (!"ar".equals(lang)) {
            sendVerificationEmail(to, verificationLink);
            return;
        }
        try {
            EmailTemplates.Content c = EmailTemplates.arabicVerification(verificationLink);
            transport.send(to, c.subject(), c.text(), c.html());
            log.info("[EMAIL] Arabic verification email sent to {}", to);
        } catch (MailException e) {
            log.error("[EMAIL] Could not send verification email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink, String lang) {
        if (!"ar".equals(lang)) {
            sendPasswordResetEmail(to, resetLink);
            return;
        }
        try {
            EmailTemplates.Content c = EmailTemplates.arabicPasswordReset(resetLink);
            transport.send(to, c.subject(), c.text(), c.html());
            log.info("[EMAIL] Arabic password reset email sent to {}", to);
        } catch (MailException e) {
            log.error("[EMAIL] Could not send password reset email to {}: {}", to, e.getMessage());
        }
    }
    @Override
    public void sendSecurityAlert(String to, String name, String title, String detail, String lang) {
        if (!"ar".equals(lang)) {
            sendSecurityAlert(to, name, title, detail);
            return;
        }
        try {
            String when = WHEN_AR.format(OffsetDateTime.now().atZoneSameInstant(APP_ZONE));
            transport.send(to, "تنبيه أمني: " + title,
                    securityTextAr(name, title, detail, when), securityHtmlAr(name, title, detail, when));
            log.info("[EMAIL] Arabic security alert sent to {}", to);
        } catch (MailException e) {
            log.error("[EMAIL] Could not send security alert to {}: {}", to, e.getMessage());
        }
    }

    private String securityTextAr(String name, String title, String detail, String when) {
        return """
            مرحبًا %s،

            %s
            %s
            الوقت: %s (بتوقيت بيروت)

            إذا كنت أنت من فعل ذلك، يمكنك تجاهل هذه الرسالة.
            إذا لم تكن أنت، غيّر كلمة المرور فورًا: %s/settings.html
            """.formatted(name, title, detail, when, baseUrl);
    }

    private String securityHtmlAr(String name, String title, String detail, String when) {
        return """
            <div dir="rtl" style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937;text-align:right">
              <h2 style="color:#b42318">تنبيه أمني</h2>
              <p>مرحبًا %s،</p>
              <div style="border-right:4px solid #b42318;padding:8px 12px;background:#fdf3f2;border-radius:4px">
                <p style="margin:0;font-size:16px"><strong>%s</strong></p>
                <p style="margin:4px 0 0;color:#6b7280">%s</p>
                <p style="margin:4px 0 0;color:#6b7280">الوقت: %s (بتوقيت بيروت)</p>
              </div>
              <p>إذا كنت أنت من فعل ذلك، يمكنك تجاهل هذه الرسالة.</p>
              <p style="margin:24px 0">
                <a href="%s/settings.html" style="background:#b42318;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">لم أكن أنا</a>
              </p>
              <p style="font-size:13px;color:#6b7280">يفتح الزر صفحة الإعدادات حيث يمكنك تغيير كلمة المرور.</p>
            </div>
            """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(detail),
                HtmlUtils.htmlEscape(when), HtmlUtils.htmlEscape(baseUrl));
    }

}