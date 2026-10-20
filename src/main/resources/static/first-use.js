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
    // KAN-380: each step knows when it's done. 1 and 2 look at the tasks; 3 and 4 remember the click.
    const STEPS = [
        { id: 's1', href: 'tasks.html#new-task', done: tasks => tasks.some(task => task.recurring) },
        { id: 's2', href: 'tasks.html#new-task',
          done: tasks => tasks.some(task => task.reminderEnabled || (task.reminders && task.reminders.length)) },
        { id: 's3', href: 'calendar.html', done: () => visited('s3'), remember: true },
        { id: 's4', href: 'settings.html', done: () => visited('s4'), remember: true }
    ];

    function visited(id) {
        try { return localStorage.getItem('aalsikke_guide_' + id) === '1'; } catch (_) { return false; }
    }

    function markVisited(id) {
        try { localStorage.setItem('aalsikke_guide_' + id, '1'); } catch (_) { /* private mode */ }
    }

    function dismissed() {
        try { return localStorage.getItem(DISMISS_KEY) === '1'; } catch (_) { return false; }
    }

    function remember() {
        try { localStorage.setItem(DISMISS_KEY, '1'); } catch (_) { /* private mode: it just shows again */ }
    }

    function build(tasks) {
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

            if (step.done(tasks)) {
                // Done: greyed out, crossed through, with a check instead of the button
                li.classList.add('is-done');
                li.style.opacity = '0.6';
                strong.style.textDecoration = 'line-through';
                const doneLabel = document.createElement('span');
                doneLabel.className = 'guide-done';
                doneLabel.textContent = '\u2713 ' + t('guide.done');
                li.append(text, doneLabel);
            } else {
                const a = document.createElement('a');
                a.className = 'btn-secondary guide-link';
                a.href = step.href;
                a.textContent = t(`guide.${step.id}.action`);
                if (step.remember) a.addEventListener('click', () => markVisited(step.id));
                li.append(text, a);
            }
            list.append(li);
        });

        card.append(head, list);
        return card;
    }

    function sync() {
        const app = window.AalSikke;
        const existing = document.getElementById('first-use-guide');
        if (!app || app.getTasksState() !== 'ready') return;

        // KAN-380: stays until all four steps are done (or ✕), instead of vanishing after the first task
        const tasks = app.getTasks();
        const allDone = STEPS.every(step => step.done(tasks));
        if (allDone) remember();   // finished: don't show it again

        if (allDone || dismissed()) {
            if (existing) existing.remove();
            return;
        }

        // Rebuild each time so the ✓ marks follow the tasks as they change
        const card = build(tasks);
        if (existing) {
            existing.replaceWith(card);
            return;
        }
        const anchor = document.getElementById('next-up-card') || document.getElementById('dashboard-cards') || document.querySelector('.board-panel');
        if (anchor && anchor.parentNode) {
            anchor.parentNode.insertBefore(card, anchor);
        }
    }

    document.addEventListener('aalsikke:tasks-loaded', sync);
    document.addEventListener('DOMContentLoaded', sync);
})();