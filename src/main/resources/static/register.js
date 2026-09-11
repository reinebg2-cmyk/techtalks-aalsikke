// Aal Sikke — Registration page logic
// Client-side validation stays the same; submission is wired to the real
// backend: POST /api/auth/register (see AuthController.java).

const API_BASE = window.location.origin; // same-origin, served from Spring Boot static/

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('register-form');
  const fullnameField = document.getElementById('field-fullname');
  const dobField = document.getElementById('field-dob');
  const dobInput = document.getElementById('dob');
  const emailField = document.getElementById('field-email');
  const emailInput = document.getElementById('email');
  const phoneInput = document.getElementById('phone');
  const passwordField = document.getElementById('field-password');
  const passwordInput = document.getElementById('password');
  const confirmField = document.getElementById('field-confirm');
  const confirmInput = document.getElementById('confirm-password');
  const strengthMeter = document.getElementById('strength-meter');
  const passwordHint = document.getElementById('password-hint');
  const submitBtn = document.getElementById('submit-btn');
  const togglePassword = document.getElementById('toggle-password');
  const emailHint = emailField.querySelector('.error-text');
  const defaultEmailHint = emailHint ? emailHint.textContent : '';

  togglePassword.addEventListener('click', () => {
    const isHidden = passwordInput.type === 'password';
    passwordInput.type = isHidden ? 'text' : 'password';
  });

  function setState(fieldEl, state) {
    fieldEl.classList.remove('is-success', 'is-warning', 'is-error');
    if (state) fieldEl.classList.add(state);
  }

  function validateFullname() {
    setState(fullnameField, document.getElementById('fullname').value.trim().length > 1 ? 'is-success' : null);
  }

  function calculateAge(dobValue) {
    const dob = new Date(dobValue);
    const diff = Date.now() - dob.getTime();
    return Math.floor(diff / (1000 * 60 * 60 * 24 * 365.25));
  }

  function validateDob() {
    if (!dobInput.value) return setState(dobField, null);
    const age = calculateAge(dobInput.value);
    setState(dobField, age >= 13 ? 'is-success' : 'is-error');
  }

  function validateEmail() {
    const value = emailInput.value.trim().toLowerCase();
    const isValidFormat = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    if (emailHint) emailHint.textContent = defaultEmailHint;
    if (!value) return setState(emailField, null);
    if (!isValidFormat) return setState(emailField, 'is-error');
    setState(emailField, 'is-success');
  }

  function scorePassword(value) {
    let score = 0;
    if (value.length >= 8) score++;
    if (/[A-Z]/.test(value)) score++;
    if (/[0-9]/.test(value)) score++;
    if (/[^A-Za-z0-9]/.test(value)) score++;
    return score;
  }

  function validatePassword() {
    const value = passwordInput.value;
    const score = scorePassword(value);
    strengthMeter.classList.remove('strength-1', 'strength-2', 'strength-3');

    if (!value) {
      passwordHint.style.display = 'none';
      setState(passwordField, null);
      return;
    }
    if (score <= 1) {
      strengthMeter.classList.add('strength-1');
      passwordHint.textContent = 'Too weak. Add length, a number, and a symbol.';
      setState(passwordField, 'is-error');
    } else if (score <= 3) {
      strengthMeter.classList.add('strength-2');
      passwordHint.textContent = 'Good, but add a special symbol to make it bulletproof.';
      setState(passwordField, 'is-warning');
    } else {
      strengthMeter.classList.add('strength-3');
      setState(passwordField, 'is-success');
    }
    passwordHint.style.display = score <= 3 ? 'block' : 'none';
    validateConfirm();
  }

  function validateConfirm() {
    if (!confirmInput.value) return setState(confirmField, null);
    setState(confirmField, confirmInput.value === passwordInput.value ? 'is-success' : 'is-error');
  }

  document.getElementById('fullname').addEventListener('input', validateFullname);
  dobInput.addEventListener('input', validateDob);
  emailInput.addEventListener('input', validateEmail);
  passwordInput.addEventListener('input', validatePassword);
  confirmInput.addEventListener('input', validateConfirm);

  async function registerUser(payload) {
    const res = await fetch(`${API_BASE}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    let body = null;
    try { body = await res.json(); } catch (_) { /* empty body */ }

    if (!res.ok) {
      throw { field: body && body.field, message: (body && body.message) || 'Registration failed. Please try again.' };
    }
    return body; // AuthResponse: { id, fullName, email, phoneNumber, profileMode }
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    validateFullname();
    validateDob();
    validateEmail();
    validatePassword();
    validateConfirm();

    const hasError = document.querySelectorAll('.is-error').length > 0;
    if (hasError) return;

    submitBtn.disabled = true;
    submitBtn.textContent = 'Creating account…';

    registerUser({
      fullname: document.getElementById('fullname').value.trim(),
      dob: dobInput.value,
      email: emailInput.value.trim(),
      phone: phoneInput.value.trim(),
      role: document.querySelector('input[name="role"]:checked').value,
      password: passwordInput.value,
    }).then(() => {
      submitBtn.textContent = 'Account created ✓';
      window.location.href = 'login.html';
    }).catch((err) => {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Create account →';
      if (err.field === 'email') {
        if (emailHint) emailHint.textContent = err.message;
        setState(emailField, 'is-error');
      } else if (err.field === 'phone') {
        window.alert(err.message);
      } else {
        window.alert(err.message);
      }
    });
  });
});
