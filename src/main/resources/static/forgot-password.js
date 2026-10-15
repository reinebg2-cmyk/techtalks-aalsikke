// Aal Sikke — Forgot password page logic
// Wired to POST /api/auth/forgot-password (see PasswordResetController.java).
// The server gives the same answer whether or not the email exists, so this page
// never says whether an account was found.

const API_BASE = window.location.origin;
const COOLDOWN_SECONDS = 60; // the server sends at most one new link per minute

document.addEventListener('DOMContentLoaded', () => {
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
        btn.textContent = 'Send a new link';
        return;
      }
      btn.textContent = `Send again in ${left}s`;
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
      showFieldError('Enter a valid email address.');
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Sending…';

    try {
      const res = await fetch(`${API_BASE}/api/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
      });

      let body = null;
      try { body = await res.json(); } catch (_) { /* empty body */ }

      if (!res.ok) {
        throw new Error((body && (body.message || body.error)) || 'Something went wrong. Please try again.');
      }

      showNotice(((body && body.message) || "If that email is registered, we've sent a reset link.")
        + ' If you don\'t see it in a few minutes, check your spam folder.');
      startCooldown();
    } catch (err) {
      btn.disabled = false;
      btn.textContent = 'Send reset link';
      // fetch() throws a TypeError when the server can't be reached
      showFieldError(err instanceof TypeError
        ? 'Could not reach the server. Please try again.'
        : err.message);
    }
  });
});