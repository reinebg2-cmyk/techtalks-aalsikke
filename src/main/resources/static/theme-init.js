
// KAN-239: runs synchronously in <head>, before the page paints, so the
// correct theme is applied immediately instead of flashing light-then-dark
// (or vice versa) on load. Kept as its own tiny file so every page — auth
// screens included — shares one source of truth for the storage key.
// KAN-290: also applies the cached color palette the same way (data-palette),
// so a non-default palette doesn't flash blue first. The cache is only a
// paint-time hint; palette.js re-syncs it from the server on signed-in pages.
(function () {
    var STORAGE_KEY = 'aal-sikke-theme';
    var PALETTE_KEY = 'aal-sikke-palette';
    var PALETTES = ['brand', 'ocean', 'forest', 'sunset', 'violet', 'rose', 'graphite'];

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

        var palette = localStorage.getItem(PALETTE_KEY);
        document.documentElement.setAttribute('data-palette',
            PALETTES.indexOf(palette) !== -1 ? palette : 'brand');
    } catch (error) {
        // localStorage can throw in some private-browsing modes; fall back
        // to whatever the OS/browser prefers rather than breaking the page.
        document.documentElement.setAttribute('data-theme', systemPrefersDark() ? 'dark' : 'light');
        document.documentElement.setAttribute('data-palette', 'brand');
    }
})();
