// Aal Sikke — Login page logic
// Wired to the real backend: POST /api/auth/login (see AuthController.java).

const API_BASE = window.location.origin; // same-origin, served from Spring Boot static/
const USER_ID_KEY = 'aalsikke_user_id'; // same key dashboard.js reads on load

document.addEventListener('DOMContentLoaded', () => {
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

  async function loginUser(identifier, password) {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier, password }),
    });

    let body = null;
    try { body = await res.json(); } catch (_) { /* empty body */ }

    if (!res.ok) {
      throw { message: (body && body.message) || 'Login failed. Please try again.' };
    }
    return body; // AuthResponse: { id, fullName, email, phoneNumber, profileMode }
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    passwordField.classList.remove('is-error');
    loginError.textContent = '';

    const identifier = identifierInput.value.trim();
    const password = passwordInput.value;

    if (!identifier || !password) {
      loginError.textContent = 'Please enter your email/phone and password.';
      passwordField.classList.add('is-error');
      return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent = 'Logging in…';

    loginUser(identifier, password)
        .then((user) => {
          loginBtn.textContent = 'Welcome back ✓';
          localStorage.setItem(USER_ID_KEY, user.id);
          window.location.href = 'dashboard.html';
        })
        .catch((err) => {
          loginBtn.disabled = false;
          loginBtn.textContent = 'Log in →';
          loginError.textContent = err.message;
          passwordField.classList.add('is-error');
        });
  });
});
