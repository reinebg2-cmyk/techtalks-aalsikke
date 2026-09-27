// KAN-239: wires up whichever theme control(s) exist on the current page —
// the topbar icon button (#theme-toggle-btn) and/or the settings-page switch
// (#theme-toggle). Both read/write the SAME storage key and stay in sync
// with each other, so flipping one immediately updates the other if it's
// also present on the page (e.g. settings.html has both).
// Self-contained so no page's own script (dashboard.js, calendar.js,
// login.js, register.js) needs to know about theming.
(function () {
    var STORAGE_KEY = 'aal-sikke-theme';

    var iconButton = null;
    var checkbox = null;

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function persist(theme) {
        try {
            localStorage.setItem(STORAGE_KEY, theme);
        } catch (error) {
            // Theme just won't persist across reloads in this session — non-fatal.
        }
    }

    function applyIcon(button, theme) {
        // Show the icon for the CURRENT theme, so the icon always agrees
        // with the settings switch (which also reflects current state,
        // not the state a click would move to).
        button.textContent = theme === 'dark' ? '\u{1F319}' : '\u2600\uFE0F';
        button.setAttribute('aria-label', theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
        button.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
    }

    // Pushes the given theme onto <html>, storage, and every control present
    // on this page — this is the one place that actually changes the theme.
    function setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        persist(theme);

        if (iconButton) applyIcon(iconButton, theme);
        if (checkbox) checkbox.checked = theme === 'dark';
    }

    document.addEventListener('DOMContentLoaded', function () {
        iconButton = document.getElementById('theme-toggle-btn');
        checkbox = document.getElementById('theme-toggle');

        if (!iconButton && !checkbox) return;

        // Sync whichever control(s) exist to the theme theme-init.js already
        // applied before paint, so they reflect reality on first render too.
        var theme = currentTheme();
        if (iconButton) applyIcon(iconButton, theme);
        if (checkbox) checkbox.checked = theme === 'dark';

        if (iconButton) {
            iconButton.addEventListener('click', function () {
                setTheme(currentTheme() === 'dark' ? 'light' : 'dark');
            });
        }

        if (checkbox) {
            checkbox.addEventListener('change', function () {
                setTheme(checkbox.checked ? 'dark' : 'light');
            });
        }
    });
})();