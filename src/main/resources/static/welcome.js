// Aal Sikke — Welcome page logic (KAN-288 / KAN-289).
// The page is public. If the visitor already has a session token, the
// sign-in / register calls to action are swapped for "open your dashboard"
// so returning users are one click from the app.
(function () {
    var TOKEN_KEY = 'aalsikke_token';

    function hasSession() {
        try {
            return !!localStorage.getItem(TOKEN_KEY);
        } catch (e) {
            return false;
        }
    }

    function setLink(id, text, href) {
        var el = document.getElementById(id);
        if (!el) return;
        el.textContent = text;
        el.setAttribute('href', href);
    }

    function hide(id) {
        var el = document.getElementById(id);
        if (el) el.hidden = true;
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!hasSession()) return;

        setLink('wl-nav-cta', 'Open dashboard', 'dashboard.html');
        hide('wl-nav-login');
        setLink('wl-hero-primary', 'Open your dashboard \u2192', 'dashboard.html');
        hide('wl-hero-secondary');
        setLink('wl-cta-primary', 'Open dashboard', 'dashboard.html');
        hide('wl-footer-login');
        hide('wl-footer-register');
    });
})();

