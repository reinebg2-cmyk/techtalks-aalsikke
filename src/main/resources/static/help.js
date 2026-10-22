// KAN-384: the Help page is opened from Login and from Register.
// Send its main back button to the page the user came from.
// This runs before i18n.js translates the page (on DOMContentLoaded),
// so the new key gets translated like any other.
(function () {
    const from = new URLSearchParams(window.location.search).get('from');
    if (from !== 'register') return;   // default stays "Back to log in"

    const back = document.querySelector('[data-i18n="help.back"]');
    if (!back) return;

    back.setAttribute('href', 'register.html');
    back.setAttribute('data-i18n', 'help.backRegister');
    back.textContent = '\u2190 Back to sign up';   // shown only until i18n.js translates it
})();