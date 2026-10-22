// Aal Sikke — Email verification page (KAN-273 / KAN-275)
// Opened from the link in the verification email: verify-email.html?token=...
//   POST /api/auth/verify-email        { token }
//   POST /api/auth/resend-verification { email, lang }

const API_BASE = window.location.origin;
const pageLang = () => (document.documentElement.lang || 'en').toLowerCase().startsWith('ar') ? 'ar' : 'en';

document.addEventListener('DOMContentLoaded', () => {
    document.title = t('ve.pageTitle');

    const title = document.getElementById('verify-title');
    const message = document.getElementById('verify-message');
    const loginBtn = document.getElementById('verify-login-btn');
    const resendForm = document.getElementById('resend-form');
    const resendEmail = document.getElementById('resend-email');
    const resendBtn = document.getElementById('resend-btn');
    const resendMessage = document.getElementById('resend-message');

    async function postJson(path, body) {
        const res = await fetch(`${API_BASE}${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        let data = null;
        try { data = await res.json(); } catch (_) { /* empty body */ }
        return { ok: res.ok, data };
    }

    function showFailure(text) {
        title.textContent = t('ve.failedTitle');
        message.textContent = text;
        resendForm.hidden = false;
        resendEmail.focus();
    }

    async function verify() {
        const token = new URLSearchParams(window.location.search).get('token');

        if (!token) {
            showFailure(t('ve.needLink'));
            return;
        }

        try {
            const { ok, data } = await postJson('/api/auth/verify-email', { token });

            if (ok) {
                title.textContent = t('ve.verified');
                message.textContent = t('ve.verifiedMsg');
                loginBtn.hidden = false;
                // Drop the token from the address bar so it isn't kept in history.
                history.replaceState(null, '', window.location.pathname);
                return;
            }

            showFailure(apiError(data) || t('ve.invalid'));
        } catch (_) {
            showFailure(t('auth.network'));
        }
    }

    resendForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = resendEmail.value.trim();

        if (!email) {
            resendMessage.textContent = t('ve.enterEmail');
            return;
        }

        resendBtn.disabled = true;
        try {
            await postJson('/api/auth/resend-verification', { email, lang: pageLang() });
            resendMessage.textContent = t('ve.resent');
        } catch (_) {
            resendMessage.textContent = t('auth.network');
        } finally {
            // Small pause so the button can't be spammed (the server has a 60 s cooldown too).
            setTimeout(() => { resendBtn.disabled = false; }, 5000);
        }
    });

    verify();
});