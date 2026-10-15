// KAN-290 / KAN-291: per-user color palettes + sign-out cleanup.
// Loaded on every signed-in page (dashboard, calendar, tasks, settings).
//  - Syncs the user's saved palette from the server and applies it (data-palette on <html>).
//  - Renders the palette picker when the page has #palette-grid (settings.html).
//  - Clears the token and the cached palette when the user logs out, so the next
//    person on this browser doesn't inherit the previous user's palette.
// The palette is independent of light/dark: styles.css defines each preset for both modes.
(function () {
    var TOKEN_KEY = 'aalsikke_token';
    var PALETTE_KEY = 'aal-sikke-palette';
    var API_BASE = window.location.origin;

    // id, label, and the logo colors [ink, accent] + tile tint, matching the light-mode values in styles.css.
    var PALETTES = [
        { id: 'brand',    label: 'Aal Sikke', ink: '#0F203E', accent: '#F48A40', tint: '#E6EAF2' },
        { id: 'ocean',    label: 'Ocean',     ink: '#0F4C75', accent: '#2E9BD6', tint: '#E3EEF7' },
        { id: 'forest',   label: 'Forest',    ink: '#14513A', accent: '#7DBB3C', tint: '#E4F3EC' },
        { id: 'sunset',   label: 'Sunset',    ink: '#7A3410', accent: '#F2A81D', tint: '#FCEBDD' },
        { id: 'violet',   label: 'Violet',    ink: '#3F2C7A', accent: '#9B7BEA', tint: '#ECE8F8' },
        { id: 'rose',     label: 'Rose',      ink: '#7A1F45', accent: '#F0709F', tint: '#FBE7EF' },
        { id: 'graphite', label: 'Graphite',  ink: '#1F2937', accent: '#8B97A6', tint: '#E8EBEF' }
    ];
    var IDS = PALETTES.map(function (p) { return p.id; });

    function isValid(id) { return IDS.indexOf(id) !== -1; }

    function currentPalette() {
        var value = document.documentElement.getAttribute('data-palette');
        return isValid(value) ? value : 'brand';
    }

    function cache(id) {
        try { localStorage.setItem(PALETTE_KEY, id); } catch (e) { /* non-fatal */ }
    }

    function apply(id) {
        document.documentElement.setAttribute('data-palette', isValid(id) ? id : 'brand');
    }

    function authHeaders() {
        return {
            'Authorization': 'Bearer ' + localStorage.getItem(TOKEN_KEY),
            'Content-Type': 'application/json'
        };
    }

    // ---------- server sync ----------

    function loadFromServer() {
        if (!localStorage.getItem(TOKEN_KEY)) return Promise.resolve(null);
        return fetch(API_BASE + '/api/users/me/palette', { headers: authHeaders() })
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (body) {
                if (body && isValid(body.palette)) {
                    apply(body.palette);
                    cache(body.palette);
                    return body.palette;
                }
                return null;
            })
            .catch(function () { return null; }); // offline/401: keep whatever is already applied
    }

    function saveToServer(id) {
        return fetch(API_BASE + '/api/users/me/palette', {
            method: 'PUT',
            headers: authHeaders(),
            body: JSON.stringify({ palette: id })
        }).then(function (res) {
            if (!res.ok) throw new Error('Could not save your palette.');
            return res.json();
        });
    }

    // ---------- settings picker (KAN-290) ----------

    function toast(message) {
        var el = document.getElementById('toast');
        if (!el) return;
        el.textContent = message;
        el.classList.add('is-visible');
        setTimeout(function () { el.classList.remove('is-visible'); }, 2500);
    }

    function renderPicker(grid) {
        var message = document.getElementById('palette-message');
        var selected = currentPalette();

        function mark() {
            Array.prototype.forEach.call(grid.children, function (btn) {
                var on = btn.getAttribute('data-palette-id') === selected;
                btn.setAttribute('aria-checked', on ? 'true' : 'false');
                btn.classList.toggle('is-selected', on);
            });
        }

        grid.innerHTML = '';
        PALETTES.forEach(function (p) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'palette-option';
            btn.setAttribute('role', 'radio');
            btn.setAttribute('data-palette-id', p.id);
            btn.setAttribute('aria-label', p.label + ' palette');

            // preview tile: the real logo drawn with this palette's colors (inline custom properties reach the <use> tree)
            var chips = document.createElement('span');
            chips.className = 'palette-preview';
            chips.style.background = p.tint;
            chips.setAttribute('aria-hidden', 'true');
            chips.innerHTML = '<svg viewBox="0 0 578 240" style="--logo-ink:' + p.ink + ';--logo-accent:' + p.accent
                + '"><use href="logo-sprite.svg#aal-mark"/></svg>';

            var name = document.createElement('span');
            name.className = 'palette-name';
            name.textContent = p.label;

            btn.appendChild(chips);
            btn.appendChild(name);

            btn.addEventListener('click', function () {
                if (p.id === selected) return;
                var previous = selected;
                selected = p.id;
                apply(p.id);      // instant feedback
                cache(p.id);
                mark();
                if (message) message.textContent = '';

                saveToServer(p.id)
                    .then(function () { toast(p.label + ' palette saved'); })
                    .catch(function () {
                        // roll back so the UI never shows a palette the server doesn't have
                        selected = previous;
                        apply(previous);
                        cache(previous);
                        mark();
                        if (message) message.textContent = 'Could not save your palette. Please try again.';
                    });
            });

            grid.appendChild(btn);
        });
        mark();
    }

    // ---------- logout cleanup ----------

    function wireLogout() {
        var link = document.getElementById('logout-link');
        if (!link) return;
        link.addEventListener('click', function () {
            try {
                localStorage.removeItem(TOKEN_KEY);
                localStorage.removeItem(PALETTE_KEY);
            } catch (e) { /* non-fatal */ }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        wireLogout();
        var grid = document.getElementById('palette-grid');
        if (grid) renderPicker(grid);
        loadFromServer().then(function (id) {
            // re-render so the picker reflects the saved choice if it differed from the cache
            if (grid && id) renderPicker(grid);
        });
    });
})();
