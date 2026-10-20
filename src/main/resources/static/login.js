// Aal Sikke — Login page logic
// Wired to the real backend: POST /api/auth/login (see AuthController.java).

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token'; // dashboard.js will read this to authenticate requests
const pageLang = () => (document.documentElement.lang || 'en').toLowerCase().startsWith('ar') ? 'ar' : 'en';

document.addEventListener('DOMContentLoaded', () => {
  document.querySelector('.back-btn')?.addEventListener('click', () => {
    window.location.href = 'index.html';
  });
  const form = document.getElementById('login-form');
  const identifierField = document.getElementById('field-identifier');
  const identifierInput = document.getElementById('identifier');
  const passwordField = document.getElementById('field-login-password');
  const passwordInput = document.getElementById('login-password');
  const loginError = document.getElementById('login-error');
  const loginBtn = document.getElementById('login-btn');
  const togglePassword = document.getElementById('toggle-login-password');

  togglePassword.addEventListener('click', () => {
    const isHidden = passwordInput.type === 'password';
    passwordInput.type = isHidden ? 'text' : 'password';
  });

  identifierInput.addEventListener('input', () => {
    identifierField.classList.toggle('is-success', identifierInput.value.trim().length > 3);
  });

  async function loginUser(email, password, rememberMe) {
    let res;
    try {
      res = await fetch(`${API_BASE}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, rememberMe, lang: pageLang()}), // matches real LoginRequest DTO
      });
    } catch (_) {
      // fetch() throws a TypeError ("Failed to fetch" / "Load failed") when the server can't be reached
      throw { message: t('auth.network') };
    }

    let body = null;
    try { body = await res.json(); } catch (_) { /* empty body */ }

    if (!res.ok) {
      throw {
        message: apiError(body, 'login.failed'),
        code: body && body.code,     // KAN-274: 'EMAIL_NOT_VERIFIED'
        email: body && body.email
      };
    }
    return body; // real AuthResponse: { token, message }
  }

  // ============================================================
  // KAN-262: email verification notices + resend
  // ============================================================

  const notice = document.getElementById('login-notice');
  const noticeText = document.getElementById('login-notice-text');
  const resendBtn = document.getElementById('resend-verification-btn');
  let unverifiedEmail = null;

  function showNotice(text, { withResend = false } = {}) {
    if (!notice) return;
    noticeText.textContent = text;
    resendBtn.hidden = !withResend;
    notice.hidden = false;
  }

  // KAN-338: account-data.js sends people here after deleting their account
  if (new URLSearchParams(window.location.search).has('account-deleted')) {
    showNotice(t('login.accountDeleted'));
  }
  // register.js sends new users here with ?check-email=1
  if (new URLSearchParams(window.location.search).has('check-email')) {
    showNotice(t('login.checkEmail'));
  }

  if (resendBtn) {
    resendBtn.addEventListener('click', async () => {
      if (!unverifiedEmail) return;
      resendBtn.disabled = true;
      try {
        const res = await fetch(`${API_BASE}/api/auth/resend-verification`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: unverifiedEmail,  lang: pageLang() })
        });
        let body = null;
        try { body = await res.json(); } catch (_) { /* empty body */ }
        if (res.ok) {
          showNotice(t('login.resendDone'));
        } else {
          showNotice(apiError(body, 'login.failed'), { withResend: true });
        }
      } catch (_) {
        showNotice(t('login.serverUnreachable'), { withResend: true });
      } finally {
        // The server allows one new link per minute.
        setTimeout(() => { resendBtn.disabled = false; }, 60000);
      }
    });
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    passwordField.classList.remove('is-error');
    loginError.textContent = '';

    const email = identifierInput.value.trim();
    const password = passwordInput.value;
    const rememberMe = document.getElementById('remember-device')?.checked ?? false;

    if (!email || !password) {
      loginError.textContent =t('login.enterBoth');
      passwordField.classList.add('is-error');
      return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent =t('login.loggingIn');

    loginUser(email, password, rememberMe)
        .then((auth) => {
          loginBtn.textContent = t('login.welcomeBack');
          localStorage.removeItem(TOKEN_KEY);
          sessionStorage.removeItem(TOKEN_KEY);
          (rememberMe ? localStorage : sessionStorage).setItem(TOKEN_KEY, auth.token);
          window.location.href = 'dashboard.html';
        })
        .catch((err) => {
          loginBtn.disabled = false;
          loginBtn.textContent = t('login.submit');

          // KAN-274: right password, email not verified yet
          if (err.code === 'EMAIL_NOT_VERIFIED') {
            unverifiedEmail = err.email || null;
            showNotice(t('err.emailNotVerified'), { withResend: Boolean(unverifiedEmail) });
            return;
          }

          loginError.textContent = err.message;
          passwordField.classList.add('is-error');
        });
  });
});