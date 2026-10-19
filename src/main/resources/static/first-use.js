// Aal Sikke — first-use guide on the dashboard (Sprint 6)
//
// A new user lands on an empty dashboard. This shows a short "Get started" card with
// the four things the app is for. It appears only while the user has no tasks and has
// not closed it, and it goes away by itself after the first task is added.
// Needs dashboard.js (window.AalSikke) and the 'aalsikke:tasks-loaded' event.
// Needs i18n.js loaded first (t).
(function () {

    const DISMISS_KEY = 'aalsikke_guide_dismissed';

    // Text comes from t('guide.<id>.title' / '.text' / '.action'); only the links live here.
    const STEPS = [
        { id: 's1', href: 'tasks.html#new-task' },
        { id: 's2', href: 'tasks.html#new-task' },
        { id: 's3', href: 'calendar.html' },
        { id: 's4', href: 'settings.html' }
    ];

    function dismissed() {
        try { return localStorage.getItem(DISMISS_KEY) === '1'; } catch (_) { return false; }
    }

    function remember() {
        try { localStorage.setItem(DISMISS_KEY, '1'); } catch (_) { /* private mode: it just shows again */ }
    }

    function build() {
        const card = document.createElement('section');
        card.className = 'card guide-card';
        card.id = 'first-use-guide';
        card.setAttribute('aria-labelledby', 'guide-heading');

        const head = document.createElement('div');
        head.className = 'guide-head';

        const titles = document.createElement('div');
        const h2 = document.createElement('h2');
        h2.id = 'guide-heading';
        h2.textContent = t('guide.title');
        const sub = document.createElement('p');
        sub.className = 'card-subtitle';
        sub.textContent = t('guide.subtitle');
        titles.append(h2, sub);

        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'guide-close';
        close.setAttribute('aria-label', t('guide.hide'));
        close.textContent = '\u2715';
        close.addEventListener('click', () => {
            remember();
            card.remove();
        });

        head.append(titles, close);

        const list = document.createElement('ol');
        list.className = 'guide-steps';
        STEPS.forEach(step => {
            const li = document.createElement('li');

            const text = document.createElement('div');
            const strong = document.createElement('strong');
            strong.textContent = t(`guide.${step.id}.title`);
            const p = document.createElement('p');
            p.textContent = t(`guide.${step.id}.text`);
            text.append(strong, p);

            const a = document.createElement('a');
            a.className = 'btn-secondary guide-link';
            a.href = step.href;
            a.textContent = t(`guide.${step.id}.action`);

            li.append(text, a);
            list.append(li);
        });

        card.append(head, list);
        return card;
    }

    function sync() {
        const app = window.AalSikke;
        const existing = document.getElementById('first-use-guide');
        if (!app || app.getTasksState() !== 'ready') return;

        const show = app.getTasks().length === 0 && !dismissed();

        if (!show) {
            if (existing) existing.remove();
            return;
        }
        if (existing) return;

        const anchor = document.getElementById('next-up-card') || document.getElementById('dashboard-cards') || document.querySelector('.board-panel');
        if (anchor && anchor.parentNode) {
            anchor.parentNode.insertBefore(build(), anchor);
        }
    }

    document.addEventListener('aalsikke:tasks-loaded', sync);
    document.addEventListener('DOMContentLoaded', sync);
})();