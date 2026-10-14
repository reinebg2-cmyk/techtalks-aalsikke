// Aal Sikke — Reset password page logic
// Wired to POST /api/auth/reset-password (see PasswordResetController.java).
// The link from the email looks like reset-password.html?token=...

const API_BASE = window.location.origin;
const MIN_LENGTH = 8;   // TODO: keep in sync with the registration rules
const MAX_LENGTH = 72;  // BCrypt only uses the first 72 bytes

document.addEventListener('DOMContentLoaded', () => {
  document.querySelector('.back-btn')?.addEventListener('click', () => {
    window.location.href = 'login.html';
  });

  const token = new URLSearchParams(window.location.search).get('token');

  const form = document.getElementById('reset-form');
  const fields = document.getElementById('reset-fields');
  const newField = document.getElementById('field-new-password');
  const newInput = document.getElementById('new-password');
  const newError = document.getElementById('new-password-error');
  const confirmField = document.getElementById('field-confirm-password');
  const confirmInput = document.getElementById('confirm-password');
  const confirmError = document.getElementById('confirm-error');
  const meter = document.getElementById('strength-meter');
  const toggle = document.getElementById('toggle-new-password');
  const btn = document.getElementById('reset-btn');
  const notice = document.getElementById('reset-notice');
  const noticeText = document.getElementById('reset-notice-text');
  const requestNewLink = document.getElementById('request-new-link');
  const goLogin = document.getElementById('go-login');
  const backRow = document.getElementById('back-row');

  function showNotice(text, { requestNew = false } = {}) {
    noticeText.textContent = text;
    requestNewLink.hidden = !requestNew;
    notice.hidden = false;
  }

  function setError(field, hint, text) {
    hint.textContent = text;
    field.classList.add('is-error');
  }

  function clearErrors() {
    newField.classList.remove('is-error');
    confirmField.classList.remove('is-error');
    notice.hidden = true;
  }

  // No token in the address: the link was cut off when it was copied.
  if (!token) {
    fields.hidden = true;
    showNotice('This reset link is incomplete. Please request a new one.', { requestNew: true });
    return;
  }

  toggle.addEventListener('click', () => {
    const hide = newInput.type === 'text';
    newInput.type = hide ? 'password' : 'text';
    confirmInput.type = hide ? 'password' : 'text';
  });

  // 0 = empty, 1 = weak, 2 = okay, 3 = strong
  function strengthOf(password) {
    if (!password) return 0;
    let score = 0;
    if (password.length >= MIN_LENGTH) score += 1;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password) && /\d/.test(password)) score += 1;
    if (password.length >= 12 || /[^A-Za-z0-9]/.test(password)) score += 1;
    return Math.max(1, score);
  }

  newInput.addEventListener('input', () => {
    newField.classList.remove('is-error');
    meter.className = 'strength-meter';
    const level = strengthOf(newInput.value);
    if (level > 0) meter.classList.add(`strength-${level}`);
  });

  confirmInput.addEventListener('input', () => confirmField.classList.remove('is-error'));

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors();

    const password = newInput.value;
    const confirm = confirmInput.value;

    if (password.length < MIN_LENGTH) {
      setError(newField, newError, `Use at least ${MIN_LENGTH} characters.`);
      return;
    }
    if (password.length > MAX_LENGTH) {
      setError(newField, newError, `Use at most ${MAX_LENGTH} characters.`);
      return;
    }
    if (password !== confirm) {
      setError(confirmField, confirmError, 'The two passwords do not match.');
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Resetting…';

    try {
      const res = await fetch(`${API_BASE}/api/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, newPassword: password })
      });

      let body = null;
      try { body = await res.json(); } catch (_) { /* empty body */ }

      if (!res.ok) {
        throw new Error((body && (body.message || body.error)) || 'Could not reset your password. Please try again.');
      }

      // Success: clear the form, hide it and point to the login page.
      newInput.value = '';
      confirmInput.value = '';
      fields.hidden = true;
      backRow.hidden = true;
      goLogin.hidden = false;
      showNotice((body && body.message) || 'Your password has been reset. You can log in now.');
    } catch (err) {
      btn.disabled = false;
      btn.textContent = 'Reset password';

      if (err instanceof TypeError) {
        // fetch() throws a TypeError when the server can't be reached
        showNotice('Could not reach the server. Please try again.');
      } else if (/reset link/i.test(err.message)) {
        // expired, already used, or replaced by a newer link: the form can't work any more
        fields.hidden = true;
        showNotice(err.message, { requestNew: true });
      } else {
        showNotice(err.message);
      }
    }
  });
});