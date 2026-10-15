// KAN-239: wires up whichever theme control(s) exist on the current page —
// the topbar icon button (#theme-toggle-btn) and/or the settings-page switch
// (#theme-toggle). Both read/write the SAME storage key and stay in sync
// with each other, so flipping one immediately updates the other if it's
// also present on the page (e.g. settings.html has both).
// Self-contained so no page's own script (dashboard.js, calendar.js,
// login.js, register.js) needs to know about theming.
//
// Sync rules: the single source of truth is the saved choice in localStorage
// (falling back to the OS preference, exactly like theme-init.js). sync() re-reads
// it and pushes it onto <html>, the icon and the switch. It runs on load, when the
// page is restored from the back/forward cache (pageshow), when the tab becomes
// visible again, when ANOTHER tab changes the theme (storage event) and when the
// OS theme changes while no choice is saved — so the icon and the switch can never
// disagree with the page.
(function () {
    var STORAGE_KEY = 'aal-sikke-theme';

    var iconButton = null;
    var checkbox = null;

    function systemPrefersDark() {
        return !!(window.matchMedia
            && window.matchMedia('(prefers-color-scheme: dark)').matches);
    }

    function savedTheme() {
        try {
            var saved = localStorage.getItem(STORAGE_KEY);
            return (saved === 'light' || saved === 'dark') ? saved : null;
        } catch (error) {
            return null;
        }
    }

    // What the theme SHOULD be right now.
    function resolveTheme() {
        return savedTheme() || (systemPrefersDark() ? 'dark' : 'light');
    }

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
        // The icon shows the CURRENT theme (sun = light, moon = dark), the same
        // state the settings switch shows (switch on = dark).
        button.textContent = theme === 'dark' ? '\u{1F319}' : '☀️';
        button.setAttribute('aria-label', theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
        button.setAttribute('title', theme === 'dark' ? 'Dark mode (click for light)' : 'Light mode (click for dark)');
        button.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
    }

    // Pushes a theme onto <html> and every control present on this page.
    function render(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        if (iconButton) applyIcon(iconButton, theme);
        if (checkbox) checkbox.checked = theme === 'dark';
    }

    // The one place a USER action changes the theme.
    function setTheme(theme) {
        persist(theme);
        render(theme);
    }

    // Re-reads the source of truth and repaints everything from it.
    function sync() {
        render(resolveTheme());
    }

    document.addEventListener('DOMContentLoaded', function () {
        iconButton = document.getElementById('theme-toggle-btn');
        checkbox = document.getElementById('theme-toggle');

        if (iconButton || checkbox) sync();

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

    // Back/forward cache restores the old DOM without re-running scripts.
    window.addEventListener('pageshow', function (event) {
        if (event.persisted) sync();
    });

    // Tab switched back to: another tab may have changed the theme meanwhile.
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) sync();
    });

    // Another tab changed the theme (the storage event never fires in the tab that wrote it).
    window.addEventListener('storage', function (event) {
        if (event.key === STORAGE_KEY || event.key === null) sync();
    });

    // OS theme flipped and the user never chose one explicitly: follow the OS.
    if (window.matchMedia) {
        var mq = window.matchMedia('(prefers-color-scheme: dark)');
        var onSystemChange = function () { if (!savedTheme()) sync(); };
        if (mq.addEventListener) mq.addEventListener('change', onSystemChange);
        else if (mq.addListener) mq.addListener(onSystemChange);
    }
})();