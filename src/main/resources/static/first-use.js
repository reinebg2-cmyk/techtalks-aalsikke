// Aal Sikke — first-use guide on the dashboard (Sprint 6)
//
// A new user lands on an empty dashboard. This shows a short "Get started" card with
// the four things the app is for. It appears only while the user has no tasks and has
// not closed it, and it goes away by itself after the first task is added.
// Needs dashboard.js (window.AalSikke) and the 'aalsikke:tasks-loaded' event.
(function () {

    const DISMISS_KEY = 'aalsikke_guide_dismissed';

    const STEPS = [
        {
            title: 'Add your weekly classes or shifts',
            text: 'Tap New task, choose Class or Shift, tick “Has a start time” and “Repeat weekly”.',
            href: 'tasks.html#new-task',
            action: 'Add one'
        },
        {
            title: 'Add a task with a reminder',
            text: 'Give it a due date and tick “Remind me about this task”. Open its ⋮ menu and choose Edit to add checklist steps.',
            href: 'tasks.html#new-task',
            action: 'Add a task'
        },
        {
            title: 'See your week',
            text: 'The calendar shows everything in one place, plus your weekly review.',
            href: 'calendar.html',
            action: 'Open calendar'
        },
        {
            title: 'Choose how you get reminded',
            text: 'Pick your colours and notification channels in Settings.',
            href: 'settings.html',
            action: 'Open settings'
        }
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
        h2.textContent = 'Get started in four steps';
        const sub = document.createElement('p');
        sub.className = 'card-subtitle';
        sub.textContent = 'Your dashboard fills up as you add things.';
        titles.append(h2, sub);

        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'guide-close';
        close.setAttribute('aria-label', 'Hide this guide');
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
            strong.textContent = step.title;
            const p = document.createElement('p');
            p.textContent = step.text;
            text.append(strong, p);

            const a = document.createElement('a');
            a.className = 'btn-secondary guide-link';
            a.href = step.href;
            a.textContent = step.action;

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
