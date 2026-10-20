// KAN-381: Google-style popup when a reminder is due, on every logged-in page.
// Checks every 30 s and when the tab is shown again. Buttons: Dismiss / Snooze 10 min / Reschedule.
// Talks to the dashboard bell through two events, so both stay in sync without looping:
//   popup -> page:  'aalsikke:popup-changed'   (dashboard reloads the bell)
//   page  -> popup: 'aalsikke:bell-changed'    (popup checks again right away)
(function () {
    'use strict';

    const POLL_MS = 30000;
    const MAX_CARDS = 3;
    const closedThisVisit = new Set();   // ✕ hides a card until the next page load, without dismissing it
    let stack = null;
    let timer = null;
    let lastDueIds = null;

    const tr = key => (typeof t === 'function' ? t(key) : key);
    const token = () => (typeof getToken === 'function' ? getToken() : null);

    async function call(method, url, body) {
        const headers = { Authorization: `Bearer ${token()}` };
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const response = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (response.status === 401) {   // logged out: stop checking
            clearInterval(timer);
            throw new Error('session');
        }
        return response;
    }

    function formatWhen(iso) {
        return formatAppDateTime(new Date(iso));   // KAN-383: same format as the rest of the app
    }

    function toLocalInputValue(value) {
        const d = new Date(value);
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;   // textContent: titles can't inject HTML
        return node;
    }

    function button(text, className) {
        const b = el('button', className, text);
        b.type = 'button';
        return b;
    }

    function changed() {
        window.dispatchEvent(new CustomEvent('aalsikke:popup-changed'));
    }

    async function act(request, done, fail) {
        try {
            const response = await request();
            if (response.ok) return done();
            let message = null;
            try { message = (await response.json()).message; } catch (_) { /* no JSON body */ }
            fail(message);
        } catch (error) {
            if (error.message !== 'session') fail();
        }
    }

    function buildCard(reminder) {
        const id = String(reminder.id);
        const card = el('div', 'reminder-popup');
        card.dataset.id = id;
        card.setAttribute('role', 'alertdialog');

        const head = el('div', 'reminder-popup-head');
        head.appendChild(el('span', 'reminder-popup-label', '🔔 ' + tr('popup.title')));
        const close = button('✕', 'reminder-popup-close');
        close.setAttribute('aria-label', tr('popup.close'));
        close.addEventListener('click', () => { closedThisVisit.add(id); card.remove(); });
        head.appendChild(close);

        const title = el('p', 'reminder-popup-title', reminder.scheduleItemTitle || tr('popup.title'));
        const when = el('p', 'reminder-popup-time', formatWhen(reminder.remindAt));
        const error = el('p', 'reminder-popup-error');
        error.hidden = true;

        const actions = el('div', 'reminder-popup-actions');
        const dismiss = button(tr('popup.dismiss'), 'btn-secondary');
        const snooze = button(tr('popup.snooze'), 'btn-secondary');
        const reschedule = button(tr('popup.reschedule'), 'btn-primary');
        actions.append(dismiss, snooze, reschedule);

        const form = el('form', 'reminder-popup-form');
        form.hidden = true;
        const input = el('input');
        input.type = 'datetime-local';
        input.dir = 'ltr';
        const save = el('button', 'btn-primary', tr('popup.save'));
        save.type = 'submit';
        form.append(input, save);

        const fail = message => { error.textContent = message || tr('popup.failed'); error.hidden = false; };
        const done = () => { card.remove(); changed(); };

        dismiss.addEventListener('click', () =>
            act(() => call('PATCH', `/api/reminders/${id}/trigger`), done, fail));
        snooze.addEventListener('click', () =>
            act(() => call('PATCH', `/api/reminders/${id}/snooze`, { minutes: 10 }), done, fail));
        reschedule.addEventListener('click', () => {
            input.value = toLocalInputValue(Date.now() + 60 * 60 * 1000);
            actions.hidden = true;
            form.hidden = false;
            input.focus();
        });
        form.addEventListener('submit', event => {
            event.preventDefault();
            const date = new Date(input.value);
            if (!input.value || isNaN(date.getTime())) return fail(tr('notif.needTime'));
            if (date.getTime() <= Date.now()) return fail(tr('notif.pastTime'));
            act(() => call('PUT', `/api/reminders/${id}`, { remindAt: date.toISOString() }), done, fail);
        });

        card.append(head, title, when, actions, form, error);
        return card;
    }

    function render(due) {
        if (!stack) {
            stack = el('div', 'reminder-popups');
            stack.setAttribute('aria-live', 'polite');
            document.body.appendChild(stack);
        }
        const wanted = due.filter(r => !closedThisVisit.has(String(r.id))).slice(0, MAX_CARDS);
        const wantedIds = new Set(wanted.map(r => String(r.id)));

        // Remove cards that are no longer due (dismissed in the bell, snoozed, ...)
        [...stack.children].forEach(card => { if (!wantedIds.has(card.dataset.id)) card.remove(); });
        // Add new ones; existing cards stay as they are (keeps a half-typed reschedule)
        const shown = new Set([...stack.children].map(card => card.dataset.id));
        wanted.forEach(r => { if (!shown.has(String(r.id))) stack.appendChild(buildCard(r)); });
    }

    async function check() {
        if (!token() || document.hidden) return;
        try {
            const response = await call('GET', '/api/reminders/due');
            if (!response.ok) return;
            const due = await response.json();
            const ids = due.map(r => r.id).join(',');
            if (lastDueIds !== null && ids !== lastDueIds) changed();   // something became due: the bell updates too
            lastDueIds = ids;
            render(due);
        } catch (_) {
            // offline or logged out: try again next time
        }
    }

    function start() {
        if (!token()) return;
        check();
        timer = setInterval(check, POLL_MS);
        document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
        window.addEventListener('aalsikke:bell-changed', check);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();