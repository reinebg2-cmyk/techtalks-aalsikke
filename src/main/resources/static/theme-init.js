// KAN-239: runs synchronously in <head>, before the page paints, so the
// correct theme is applied immediately instead of flashing light-then-dark
// (or vice versa) on load. Kept as its own tiny file so every page — auth
// screens included — shares one source of truth for the storage key.
(function () {
    var STORAGE_KEY = 'aal-sikke-theme';

    function systemPrefersDark() {
        return window.matchMedia
            && window.matchMedia('(prefers-color-scheme: dark)').matches;
    }

    try {
        var saved = localStorage.getItem(STORAGE_KEY);
        var theme = (saved === 'light' || saved === 'dark')
            ? saved
            : (systemPrefersDark() ? 'dark' : 'light');

        document.documentElement.setAttribute('data-theme', theme);
    } catch (error) {
        // localStorage can throw in some private-browsing modes; fall back
        // to whatever the OS/browser prefers rather than breaking the page.
        document.documentElement.setAttribute('data-theme', systemPrefersDark() ? 'dark' : 'light');
    }
})();
