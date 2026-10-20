// Aal Sikke — "Next up" card (KAN-340)
//
// Answers "what do I need to do now": the next time block or class that starts
// later today, with its start time and how long until it starts.
// Uses the calendar range endpoint, so today's repeats of weekly classes count too.
// Re-fetches on 'aalsikke:tasks-loaded' (fired by dashboard.js after every load,
// create, edit, complete and delete) and refreshes the countdown every minute.
// Needs i18n.js loaded first (t, lang, LANGS).

(function () {

    /** The not-completed timed item that starts next, between now and midnight; null if none. */
    function pickNextUp(items, now) {
        const endOfDay = new Date(now);
        endOfDay.setHours(24, 0, 0, 0);

        let best = null;
        for (const item of items) {
            if (!item.startTime || item.timeType === 'DEADLINE_ONLY') continue;
            if (item.status === 'COMPLETED') continue;

            const start = new Date(item.startTime);
            if (start < now || start >= endOfDay) continue;
            if (!best || start < best.start) best = { item, start };
        }
        return best;
    }

    function minutesUntil(start, now) {
        return Math.max(0, Math.ceil((start - now) / 60000));
    }

    function formatCountdown(minutes) {
        if (minutes <= 0) return t('next.now');
        if (minutes < 60) return t('next.inMin', { n: minutes });
        const hours = Math.floor(minutes / 60);
        const rest = minutes % 60;
        return rest === 0
            ? t('next.inH', { h: hours })
            : t('next.inHM', { h: hours, m: rest });
    }

    // For quick checks in the browser console
    window.AalSikkeNextUp = { pickNextUp, minutesUntil, formatCountdown };

    document.addEventListener('DOMContentLoaded', () => {

        const app = window.AalSikke;
        if (!app) {
            return; // dashboard.js redirected to login (no token)
        }

        const card = document.getElementById('next-up-card');
        const titleEl = document.getElementById('next-up-title');
        const metaEl = document.getElementById('next-up-meta');
        if (!card || !titleEl || !metaEl) {
            return;
        }

        let todaysItems = [];
        let loadedForDay = null;   // so the card reloads by itself after midnight

        function render() {
            const now = new Date();
            const next = pickNextUp(todaysItems, now);
            card.classList.toggle('is-empty', !next);

            if (!next) {
                titleEl.textContent = t('next.none');
                metaEl.textContent = '';
                return;
            }

            const time = formatAppTime(next.start);   // KAN-383
            titleEl.textContent = next.item.title;
            metaEl.textContent = `${time} · ${formatCountdown(minutesUntil(next.start, now))}`;
        }

        async function load() {
            const dayStart = new Date();
            dayStart.setHours(0, 0, 0, 0);
            const dayEnd = new Date(dayStart);
            dayEnd.setDate(dayEnd.getDate() + 1);

            try {
                const response = await app.apiFetch(
                    `${app.API_BASE}/api/schedule-items/range`
                    + `?from=${encodeURIComponent(dayStart.toISOString())}`
                    + `&to=${encodeURIComponent(dayEnd.toISOString())}`
                );
                if (!response.ok) {
                    throw new Error(`Server responded ${response.status}`);
                }
                todaysItems = await response.json();
                loadedForDay = dayStart.toDateString();
                render();
            } catch (error) {
                if (error.sessionExpired) return;
                console.error('Failed to load the next-up card:', error);
                card.classList.add('is-empty');
                titleEl.textContent = t('next.loadFailed');
                metaEl.textContent = '';
            }
        }

        document.addEventListener('aalsikke:tasks-loaded', load);

        setInterval(() => {
            if (loadedForDay !== new Date().toDateString()) {
                load();     // a new day: fetch the new day's items
            } else {
                render();   // same day: just move the countdown on
            }
        }, 60000);
    });
})();