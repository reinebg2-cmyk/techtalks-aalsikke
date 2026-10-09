// Aal Sikke — "Suggest a time" (KAN-343)
//
// Adds a small panel to three forms and fills them with a free slot from
// GET /api/schedule-items/free-slots (busy time follows the conflict rules):
//  - new-task form (tasks.html)          -> Start + Due, ticks "Has a start time"
//  - edit-task form (tasks/dashboard)     -> Start + Due, leaves the task itself out of "busy"
//  - calendar schedule window (calendar)  -> Date + Start + End
// Each page only gets the panels whose form exists on it.

(function () {

    const API_BASE = window.location.origin;
    const TOKEN_KEY = 'aalsikke_token';
    const LENGTHS = [30, 60, 90, 120];
    const BUFFERS = [0, 10, 15, 30];

    // ---------------- helpers ----------------

    const pad = n => String(n).padStart(2, '0');
    const toDateInput = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const toTimeInput = d => `${pad(d.getHours())}:${pad(d.getMinutes())}`;
    const toDateTimeInput = d => `${toDateInput(d)}T${toTimeInput(d)}`;

    function addDays(dateString, days) {
        const [y, m, d] = dateString.split('-').map(Number);
        return toDateInput(new Date(y, m - 1, d + days));
    }

    function formatDay(dateString) {
        const [y, m, d] = dateString.split('-').map(Number);
        return new Date(y, m - 1, d).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
    }

    /** "YYYY-MM-DD" from a date/datetime-local value, never before today; today if empty. */
    function dayFrom(value) {
        const today = toDateInput(new Date());
        const day = value ? value.slice(0, 10) : '';
        return day && day >= today ? day : today;
    }

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function setValue(input, value) {
        if (!input) return;
        input.value = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function setChecked(checkbox, checked) {
        if (!checkbox || checkbox.checked === checked) return;
        checkbox.checked = checked;
        checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    }

    async function fetchSlots(params) {
        const query = new URLSearchParams(params);
        const response = await fetch(`${API_BASE}/api/schedule-items/free-slots?${query}`, {
            headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` }
        });
        if (response.status === 401) {
            localStorage.removeItem(TOKEN_KEY);
            window.location.href = 'login.html';
            throw new Error('Your session has expired.');
        }
        let data = null;
        try { data = await response.json(); } catch (_) { /* no body */ }
        if (!response.ok) {
            throw new Error((data && data.message) || `Server responded ${response.status}`);
        }
        return data;
    }

    // ---------------- the panel ----------------

    /**
     * options.idPrefix   unique prefix for element ids (labels need ids)
     * options.getDay()   the day to search ("YYYY-MM-DD")
     * options.getExcludeId()  id of the task being edited, or ''
     * options.onPick(start, end)  fill the form
     */
    function createPanel({ idPrefix, getDay, getExcludeId, onPick }) {
        const panel = el('div', 'free-slot-panel');

        const row = el('div', 'free-slot-row');
        const lengthLabel = el('label', 'free-slot-length');
        lengthLabel.htmlFor = `${idPrefix}-length`;
        lengthLabel.append('Length ');
        const lengthSelect = el('select');
        lengthSelect.id = `${idPrefix}-length`;
        LENGTHS.forEach(m => lengthSelect.append(new Option(m < 60 ? `${m} min` : `${m / 60} h`.replace('.5 h', ' h 30'), m)));
        lengthSelect.value = '60';
        lengthLabel.append(lengthSelect);

        const button = el('button', 'btn-secondary free-slot-btn', 'Suggest a time');
        button.type = 'button';
        row.append(lengthLabel, button);

        // Less common options stay folded away
        const options = el('details', 'free-slot-options');
        options.append(el('summary', '', 'Options'));
        const optionsGrid = el('div', 'free-slot-options-grid');

        const timeField = (labelText, id, value) => {
            const label = el('label', '', labelText);
            label.htmlFor = id;
            const input = el('input');
            input.type = 'time';
            input.id = id;
            input.value = value;
            input.step = '900';
            label.append(input);
            return { label, input };
        };
        const from = timeField('Between ', `${idPrefix}-from`, '08:00');
        const to = timeField('and ', `${idPrefix}-to`, '22:00');

        const bufferLabel = el('label', '', 'Gap after other tasks ');
        bufferLabel.htmlFor = `${idPrefix}-buffer`;
        const bufferSelect = el('select');
        bufferSelect.id = `${idPrefix}-buffer`;
        BUFFERS.forEach(m => bufferSelect.append(new Option(m === 0 ? 'none' : `${m} min`, m)));
        bufferLabel.append(bufferSelect);

        const weekLabel = el('label', 'check-row');
        const weekBox = el('input');
        weekBox.type = 'checkbox';
        weekBox.id = `${idPrefix}-week`;
        weekLabel.htmlFor = weekBox.id;
        weekLabel.append(weekBox, ' Next 7 days');

        optionsGrid.append(from.label, to.label, bufferLabel, weekLabel);
        options.append(optionsGrid);

        const results = el('div', 'free-slot-results');
        results.setAttribute('aria-live', 'polite');

        panel.append(row, options, results);

        let shiftDays = 0;   // "Try next day" moves the search forward without touching the form

        async function search() {
            const days = weekBox.checked ? 7 : 1;
            const day = addDays(getDay(), shiftDays);
            results.replaceChildren(el('p', 'free-slot-note', 'Looking for free time…'));
            button.disabled = true;
            try {
                const params = {
                    date: day,
                    minutes: lengthSelect.value,
                    from: from.input.value || '08:00',
                    to: to.input.value || '22:00',
                    days: String(days),
                    buffer: bufferSelect.value
                };
                const excludeId = getExcludeId();
                if (excludeId) params.excludeId = excludeId;
                render(await fetchSlots(params), day, days);
            } catch (error) {
                results.replaceChildren(el('p', 'free-slot-note is-error', error.message));
            } finally {
                button.disabled = false;
            }
        }

        function chip(slot) {
            const start = new Date(slot.start);
            const end = new Date(slot.end);
            const freeUntil = new Date(slot.freeUntil);
            const label = `${toTimeInput(start)}–${toTimeInput(end)}`;
            const extra = freeUntil > end ? `free until ${toTimeInput(freeUntil)}` : '';

            const b = el('button', 'free-slot-chip');
            b.type = 'button';
            b.append(el('span', 'free-slot-time', label));
            if (extra) b.append(el('span', 'free-slot-extra', ` · ${extra}`));
            b.setAttribute('aria-label', `Use ${label}${extra ? `, ${extra}` : ''}`);
            b.addEventListener('click', () => {
                onPick(start, end);
                results.replaceChildren(el('p', 'free-slot-note', `Filled in ${label}.`));
            });
            return b;
        }

        function render(daysFound, firstDay, days) {
            results.replaceChildren();
            const withSlots = daysFound.filter(d => d.slots.length > 0);

            if (withSlots.length === 0) {
                const span = days === 1 ? `on ${formatDay(firstDay)}` : `from ${formatDay(firstDay)} for 7 days`;
                results.append(el('p', 'free-slot-note', `No free slot ${span}.`));
                const next = el('button', 'link-btn free-slot-next', days === 1 ? 'Try next day' : 'Try the next 7 days');
                next.type = 'button';
                next.addEventListener('click', () => { shiftDays += days; search(); });
                results.append(next);
                return;
            }

            for (const day of withSlots) {
                const group = el('div', 'free-slot-day');
                if (days > 1 || day.date !== getDay()) {
                    group.append(el('p', 'free-slot-day-title', formatDay(day.date)));
                }
                const chips = el('div', 'free-slot-chips');
                day.slots.forEach(slot => chips.append(chip(slot)));
                group.append(chips);
                results.append(group);
            }
        }

        button.addEventListener('click', () => { shiftDays = 0; search(); });
        return panel;
    }

    // ---------------- wiring per form ----------------

    document.addEventListener('DOMContentLoaded', () => {
        if (!localStorage.getItem(TOKEN_KEY)) return;
        if (document.querySelector('.free-slot-panel')) return;   // already set up

        // New-task form (tasks.html)
        const newStartField = document.getElementById('field-task-start');
        if (newStartField) {
            newStartField.after(createPanel({
                idPrefix: 'new-slot',
                getDay: () => dayFrom(document.getElementById('task-due')?.value),
                getExcludeId: () => '',
                onPick: (start, end) => {
                    setChecked(document.getElementById('task-has-start'), true);
                    setValue(document.getElementById('task-start'), toDateTimeInput(start));
                    setValue(document.getElementById('task-due'), toDateTimeInput(end));
                }
            }));
        }

        // Edit-task form (tasks.html and dashboard.html)
        const editStartField = document.getElementById('field-edit-task-start');
        if (editStartField) {
            editStartField.after(createPanel({
                idPrefix: 'edit-slot',
                getDay: () => dayFrom(document.getElementById('edit-task-deadline')?.value),
                getExcludeId: () => document.getElementById('edit-task-id')?.value || '',
                onPick: (start, end) => {
                    setChecked(document.getElementById('edit-task-has-start'), true);
                    setValue(document.getElementById('edit-task-start'), toDateTimeInput(start));
                    setValue(document.getElementById('edit-task-deadline'), toDateTimeInput(end));
                }
            }));
        }

        // Calendar schedule window (calendar.html)
        const scheduleMessage = document.getElementById('schedule-message');
        if (scheduleMessage) {
            scheduleMessage.before(createPanel({
                idPrefix: 'cal-slot',
                getDay: () => dayFrom(document.getElementById('schedule-date')?.value),
                getExcludeId: () => document.getElementById('schedule-modal')?.dataset.itemId
                    || document.getElementById('schedule-task')?.value || '',
                onPick: (start, end) => {
                    setChecked(document.getElementById('schedule-start-only'), false);
                    setValue(document.getElementById('schedule-date'), toDateInput(start));
                    setValue(document.getElementById('schedule-start'), toTimeInput(start));
                    setValue(document.getElementById('schedule-end'), toTimeInput(end));
                }
            }));
        }
    });
})();