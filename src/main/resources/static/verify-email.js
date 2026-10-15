// Aal Sikke — Email verification page (KAN-273 / KAN-275)
// Opened from the link in the verification email: verify-email.html?token=...
//   POST /api/auth/verify-email        { token }
//   POST /api/auth/resend-verification { email }

const API_BASE = window.location.origin;

document.addEventListener('DOMContentLoaded', () => {
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
        title.textContent = 'This link didn\'t work';
        message.textContent = text;
        resendForm.hidden = false;
        resendEmail.focus();
    }

    async function verify() {
        const token = new URLSearchParams(window.location.search).get('token');

        if (!token) {
            showFailure('This page needs the link from your verification email. Enter your email to get a new one.');
            return;
        }

        try {
            const { ok, data } = await postJson('/api/auth/verify-email', { token });

            if (ok) {
                title.textContent = 'Email verified ✓';
                message.textContent = (data && data.message) || 'You can log in now.';
                loginBtn.hidden = false;
                // Drop the token from the address bar so it isn't kept in history.
                history.replaceState(null, '', window.location.pathname);
                return;
            }

            showFailure((data && data.message) || 'This verification link is invalid or has expired.');
        } catch (_) {
            showFailure('Could not reach the server. Please try again.');
        }
    }

    resendForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = resendEmail.value.trim();

        if (!email) {
            resendMessage.textContent = 'Please enter your email.';
            return;
        }

        resendBtn.disabled = true;
        try {
            const { data } = await postJson('/api/auth/resend-verification', { email });
            resendMessage.textContent = (data && data.message) || 'If this account still needs verifying, a new link is on its way.';
        } catch (_) {
            resendMessage.textContent = 'Could not reach the server. Please try again.';
        } finally {
            // Small pause so the button can't be spammed (the server has a 60 s cooldown too).
            setTimeout(() => { resendBtn.disabled = false; }, 5000);
        }
    });

    verify();
});