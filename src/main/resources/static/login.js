// Aal Sikke — Login page logic
// Front-end only for now. Replace `mockLoginUser` with a real fetch/axios
// call to Developer 1's /api/auth/login endpoint in Task 3.3.

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

  // Mock auth — swap for a real API call later.
  function mockLoginUser(identifier, password) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        if (identifier === 'alex.chen@university.edu' && password === 'correct-password') {
          resolve({ token: 'mock_token_abc' });
        } else {
          reject({ message: 'Incorrect password. 2 attempts remaining.' });
        }
      }, 600);
    });
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    passwordField.classList.remove('is-error');

    loginBtn.disabled = true;
    loginBtn.textContent = 'Logging in…';

    mockLoginUser(identifierInput.value.trim(), passwordInput.value)
      .then(() => {
        loginBtn.textContent = 'Welcome back ✓';
        // window.location.href = 'dashboard.html';
      })
      .catch((err) => {
        loginBtn.disabled = false;
        loginBtn.textContent = 'Log in →';
        loginError.textContent = err.message;
        passwordField.classList.add('is-error');
      });
  });
});
