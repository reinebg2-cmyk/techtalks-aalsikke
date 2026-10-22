// Aal Sikke — Reset password page logic
// Wired to POST /api/auth/reset-password (see PasswordResetController.java).
// The link from the email looks like reset-password.html?token=...

const API_BASE = window.location.origin;
const MIN_LENGTH = 8;   // TODO: keep in sync with the registration rules
const MAX_LENGTH = 72;  // BCrypt only uses the first 72 bytes

document.addEventListener('DOMContentLoaded', () => {
  document.title = t('rp.pageTitle');

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
    showNotice(t('rp.incomplete'), { requestNew: true });
    return;
  }

  // KAN-386: check the link as soon as the page opens, so a used, expired or replaced link
  // shows the notice right away instead of after typing a new password.
  fields.hidden = true;
  fetch(`${API_BASE}/api/auth/reset-password/check`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token })
  })
      .then(async (res) => {
        if (res.ok) {
          fields.hidden = false;
          return;
        }
        let body = null;
        try { body = await res.json(); } catch (_) { /* empty body */ }
        if (/(LINK|TOKEN)/i.test((body && body.code) || '')) {
          showNotice(apiError(body) || t('rp.failed'), { requestNew: true });
        } else {
          fields.hidden = false;   // any other problem: keep the old behaviour, submitting will report it
        }
      })
      .catch(() => {
        fields.hidden = false;     // server unreachable: show the form, submitting shows the network message
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
      setError(newField, newError, t('rp.tooShort').replace('{n}', MIN_LENGTH));
      return;
    }
    if (password.length > MAX_LENGTH) {
      setError(newField, newError, t('rp.tooLong').replace('{n}', MAX_LENGTH));
      return;
    }
    if (password !== confirm) {
      setError(confirmField, confirmError, t('rp.mismatch'));
      return;
    }

    btn.disabled = true;
    btn.textContent = t('rp.submitting');

    try {
      const res = await fetch(`${API_BASE}/api/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, newPassword: password })
      });

      let body = null;
      try { body = await res.json(); } catch (_) { /* empty body */ }

      if (!res.ok) {
        throw { message: apiError(body) || t('rp.failed'), code: body && body.code };
      }

      // Success: clear the form, hide it and point to the login page.
      newInput.value = '';
      confirmInput.value = '';
      fields.hidden = true;
      backRow.hidden = true;
      goLogin.hidden = false;
      showNotice(t('rp.success'));
    } catch (err) {
      btn.disabled = false;
      btn.textContent = t('rp.submit');

      if (err instanceof TypeError) {
        // fetch() throws a TypeError when the server can't be reached
        showNotice(t('auth.network'));
      } else if (/(LINK|TOKEN)/i.test(err.code || '')) {
        // expired, already used, or replaced by a newer link: the form can't work any more.
        // NOTE: matches the error code names in PasswordResetService; adjust if they differ.
        fields.hidden = true;
        showNotice(err.message, { requestNew: true });
      } else {
        showNotice(err.message);
      }
    }
  });
});