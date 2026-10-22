// Aal Sikke — Forgot password page logic
// Wired to POST /api/auth/forgot-password (see PasswordResetController.java).
// The server gives the same answer whether or not the email exists, so this page
// never says whether an account was found.

const API_BASE = window.location.origin;
const COOLDOWN_SECONDS = 60; // the server sends at most one new link per minute
// The email is written in the language the page is shown in.
const pageLang = () => (document.documentElement.lang || 'en').toLowerCase().startsWith('ar') ? 'ar' : 'en';

document.addEventListener('DOMContentLoaded', () => {
  document.title = t('fp.pageTitle');

  document.querySelector('.back-btn')?.addEventListener('click', () => {
    window.location.href = 'login.html';
  });

  const form = document.getElementById('forgot-form');
  const field = document.getElementById('field-email');
  const emailInput = document.getElementById('email');
  const emailError = document.getElementById('email-error');
  const btn = document.getElementById('forgot-btn');
  const notice = document.getElementById('forgot-notice');
  const noticeText = document.getElementById('forgot-notice-text');

  const looksLikeEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

  function showNotice(text) {
    noticeText.textContent = text;
    notice.hidden = false;
  }

  function showFieldError(text) {
    emailError.textContent = text;
    field.classList.add('is-error');
  }

  emailInput.addEventListener('input', () => {
    field.classList.remove('is-error');
    field.classList.toggle('is-success', looksLikeEmail(emailInput.value.trim()));
  });

  // Disables the button for a minute after a successful request, with a visible countdown.
  function startCooldown() {
    let left = COOLDOWN_SECONDS;
    btn.disabled = true;
    const tick = () => {
      if (left <= 0) {
        btn.disabled = false;
        btn.textContent = t('fp.sendAgain');
        return;
      }
      btn.textContent = t('fp.sendAgainIn').replace('{s}', left);
      left -= 1;
      setTimeout(tick, 1000);
    };
    tick();
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    field.classList.remove('is-error');
    notice.hidden = true;

    const email = emailInput.value.trim();
    if (!looksLikeEmail(email)) {
      showFieldError(t('fp.invalidEmail'));
      return;
    }

    btn.disabled = true;
    btn.textContent = t('fp.sending');

    try {
      const res = await fetch(`${API_BASE}/api/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, lang: pageLang() })
      });

      let body = null;
      try { body = await res.json(); } catch (_) { /* empty body */ }

      if (!res.ok) {
        throw { message: apiError(body) || t('fp.generic') };
      }

      // Our own text, not the server's, so it is always in the page language.
      showNotice(t('fp.sent') + ' ' + t('fp.spam'));
      startCooldown();
    } catch (err) {
      btn.disabled = false;
      btn.textContent = t('fp.send');
      // fetch() throws a TypeError when the server can't be reached
      showFieldError(err instanceof TypeError ? t('auth.network') : err.message);
    }
  });
});