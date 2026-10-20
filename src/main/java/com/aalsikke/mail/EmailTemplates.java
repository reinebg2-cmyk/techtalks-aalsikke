package com.aalsikke.mail;

public final class EmailTemplates {
    private EmailTemplates() {}

    public record Content(String subject, String html, String text) {}

    public static Content arabicVerification(String link) {
        return arabic(
                "تأكيد بريدك الإلكتروني في عالسكة",
                "أهلًا بك في عالسكة",
                "اضغط على الزر أدناه لتأكيد بريدك الإلكتروني وتفعيل حسابك. الرابط صالح لمدة ساعة واحدة ويعمل مرة واحدة فقط.",
                "تأكيد البريد الإلكتروني",
                link,
                "إذا لم تنشئ حسابًا في عالسكة، يمكنك تجاهل هذه الرسالة.");
    }

    public static Content arabicPasswordReset(String link) {
        return arabic(
                "إعادة تعيين كلمة المرور في عالسكة",
                "إعادة تعيين كلمة المرور",
                "تلقّينا طلبًا لإعادة تعيين كلمة مرور حسابك. اضغط على الزر أدناه لاختيار كلمة مرور جديدة. الرابط صالح لمدة 30 دقيقة ويعمل مرة واحدة فقط.",
                "اختيار كلمة مرور جديدة",
                link,
                "إذا لم تطلب ذلك، يمكنك تجاهل هذه الرسالة وستبقى كلمة مرورك كما هي.");
    }

    private static Content arabic(String subject, String title, String intro,
                                  String button, String link, String footer) {
        String safeLink = link.replace("&", "&amp;").replace("\"", "&quot;");
        String html = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f5f7;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" dir="rtl" style="background:#f4f5f7;padding:24px 0;">
              <tr><td align="center">
                <table role="presentation" width="480" cellpadding="0" cellspacing="0" dir="rtl"
                       style="max-width:480px;width:100%;background:#ffffff;border-radius:12px;font-family:Tahoma,'Segoe UI',Arial,sans-serif;text-align:right;direction:rtl;">
                  <tr><td style="padding:32px;">
                    <h1 style="margin:0 0 16px;font-size:22px;color:#2C6E9E;">{{TITLE}}</h1>
                    <p style="margin:0 0 24px;font-size:15px;line-height:1.8;color:#333;">{{INTRO}}</p>
                    <p style="margin:0 0 24px;"><a href="{{LINK}}" style="display:inline-block;background:#2C6E9E;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:8px;font-size:15px;font-weight:bold;">{{BUTTON}}</a></p>
                    <p style="margin:0 0 6px;font-size:13px;color:#666;">إذا لم يعمل الزر، انسخ هذا الرابط والصقه في المتصفح:</p>
                    <p dir="ltr" style="margin:0 0 24px;font-size:12px;word-break:break-all;text-align:left;"><a href="{{LINK}}" style="color:#2C6E9E;">{{LINK}}</a></p>
                    <p style="margin:0;font-size:13px;line-height:1.7;color:#888;">{{FOOTER}}</p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body>
            </html>
            """
                .replace("{{TITLE}}", title).replace("{{INTRO}}", intro)
                .replace("{{BUTTON}}", button).replace("{{FOOTER}}", footer)
                .replace("{{LINK}}", safeLink);

        String text = title + "\n\n" + intro + "\n\n" + link + "\n\n" + footer;
        return new Content(subject, html, text);
    }
}