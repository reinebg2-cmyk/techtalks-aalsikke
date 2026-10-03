// Aal Sikke — Login page logic
// Wired to the real backend: POST /api/auth/login (see AuthController.java).

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token'; // dashboard.js will read this to authenticate requests

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

  async function loginUser(email, password) {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }), // matches real LoginRequest DTO
    });

    let body = null;
    try { body = await res.json(); } catch (_) { /* empty body */ }

    if (!res.ok) {
      throw { message: (body && body.message) || (body && body.error) || 'Login failed. Please try again.' };
    }
    return body; // real AuthResponse: { token, message }
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    passwordField.classList.remove('is-error');
    loginError.textContent = '';

    const email = identifierInput.value.trim();
    const password = passwordInput.value;

    if (!email || !password) {
      loginError.textContent = 'Please enter your email, phone or username and your password.';
      passwordField.classList.add('is-error');
      return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent = 'Logging in…';

    loginUser(email, password)
        .then((auth) => {
          loginBtn.textContent = 'Welcome back ✓';
          localStorage.setItem(TOKEN_KEY, auth.token); // store the REAL JWT
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