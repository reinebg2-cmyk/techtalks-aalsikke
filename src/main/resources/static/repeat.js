// Aal Sikke — "Repeat weekly" in the task forms (KAN-353)
//
// Adds a "Repeat weekly" option (days, every N weeks, until) to the New task form
// and the Edit task window. dashboard.js calls:
//   AalSikkeRepeat.check(kind, payload)     before saving the task (false = stop, the user was told why)
//   AalSikkeRepeat.afterSave(kind, task)    after the task is saved: creates, updates or removes the repeat
// kind is 'new' or 'edit'. labelHtml(task) is the small "Weekly" label on task cards.
// Repeats use /api/recurrence-rules (POST creates or replaces, DELETE removes).

(function () {

    const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
    const DAY_NAMES = { MON: 'Mon', TUE: 'Tue', WED: 'Wed', THU: 'Thu', FRI: 'Fri', SAT: 'Sat', SUN: 'Sun' };

    // ---------------- small helpers (pure) ----------------

    /** 'MON'..'SUN' for a datetime-local value ("2026-10-12T09:00") or an ISO string; '' if invalid. */
    function weekdayOf(value) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        return DAYS[(date.getDay() + 6) % 7];   // getDay(): Sunday = 0
    }

    /** Local calendar date "YYYY-MM-DD" of an ISO string or datetime-local value; '' if invalid. */
    function localDateOf(value) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        const pad = n => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    /** The problem with a repeat's settings, or '' when they're fine. */
    function problemWith(settings, startTime, endTime) {
        if (!startTime || !endTime) {
            return 'A task needs a start time to repeat weekly. Tick "Has a start time", or untick "Repeat weekly".';
        }
        if (!settings.days.length) return 'Pick at least one day for the weekly repeat.';
        const every = Number(settings.every);
        if (!Number.isInteger(every) || every < 1 || every > 52) {
            return '"Every" must be a whole number of weeks from 1 to 52.';
        }
        if (settings.until && settings.until < localDateOf(startTime)) {
            return '"Until" can\'t be before the task\'s start date.';
        }
        return '';
    }

    function labelHtml(task) {
        return task && task.recurring
            ? '<span class="repeat-label" title="Repeats weekly">&#8635; Weekly</span>'
            : '';
    }

    // ---------------- form sections ----------------

    const sections = {};   // 'new' / 'edit' -> section

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function buildSection(prefix, hasStart, startInput) {
        const field = el('div', 'field repeat-field');

        const toggleLabel = el('label', 'check-row');
        const box = el('input');
        box.type = 'checkbox';
        box.id = `${prefix}-repeat`;
        toggleLabel.append(box, ' Repeat weekly');

        const note = el('p', 'repeat-note', 'Add a start time to make this task repeat.');

        const details = el('div', 'repeat-details');
        details.hidden = true;

        const days = el('fieldset', 'repeat-days');
        const legend = el('legend', '', 'On');
        days.append(legend);
        let autoDay = false;   // true while the day was picked from the start time, not by the user
        const dayBoxes = DAYS.map(code => {
            const label = el('label', 'repeat-day');
            const input = el('input');
            input.type = 'checkbox';
            input.value = code;
            input.addEventListener('change', () => { autoDay = false; });   // the user chose the days
            label.append(input, el('span', '', DAY_NAMES[code]));
            days.append(label);
            return input;
        });

        const row = el('div', 'repeat-row');
        const everyLabel = el('label');
        const every = el('input', 'repeat-every');
        every.type = 'number';
        every.id = `${prefix}-repeat-every`;
        every.min = '1';
        every.max = '52';
        every.step = '1';
        every.value = '1';
        everyLabel.append('Every ', every, ' week(s)');

        const untilLabel = el('label');
        const until = el('input', 'repeat-until');
        until.type = 'date';
        until.id = `${prefix}-repeat-until`;
        untilLabel.append('Until ', until, el('span', 'repeat-optional', '(optional)'));
        row.append(everyLabel, untilLabel);

        details.append(days, row);
        field.append(toggleLabel, note, details);

        const section = {
            field, box, dayBoxes, every, until,
            rule: null,          // the task's current repeat (edit window only)
            loading: false,

            sync() {
                const canRepeat = Boolean(hasStart && hasStart.checked);
                if (!canRepeat && box.checked) box.checked = false;   // no start time = no repeat
                box.disabled = !canRepeat || section.loading;
                note.hidden = canRepeat;
                details.hidden = !box.checked;
                if (startInput) until.min = localDateOf(startInput.value);
            },

            read() {
                return {
                    days: dayBoxes.filter(b => b.checked).map(b => b.value),
                    every: every.value.trim(),
                    until: until.value
                };
            },

            fill(rule) {
                const chosen = (rule && rule.daysOfWeek ? rule.daysOfWeek.split(',') : []);
                dayBoxes.forEach(b => { b.checked = chosen.includes(b.value); });
                every.value = String((rule && rule.intervalValue) || 1);
                until.value = (rule && rule.untilDate) || '';
                box.checked = Boolean(rule);
                autoDay = false;
                section.sync();
            }
        };

        box.addEventListener('change', () => {
            // First time on: default to the weekday of the start time
            if (box.checked && !dayBoxes.some(b => b.checked)) {
                const day = weekdayOf(startInput && startInput.value);
                dayBoxes.forEach(b => { b.checked = b.value === day; });
                autoDay = true;
            }
            section.sync();
        });
        if (hasStart) hasStart.addEventListener('change', () => section.sync());
        // While the day still comes from the start time, follow the start time when it changes
        const followStart = () => {
            if (box.checked && autoDay) {
                const day = weekdayOf(startInput.value);
                if (day) dayBoxes.forEach(b => { b.checked = b.value === day; });
            }
            section.sync();
        };
        if (startInput) {
            startInput.addEventListener('change', followStart);
            startInput.addEventListener('input', followStart);
        }

        section.sync();
        return section;
    }

    // ---------------- called by dashboard.js ----------------

    function check(kind, payload) {
        const section = sections[kind];
        if (!section || !section.box.checked) return true;
        const problem = problemWith(section.read(), payload && payload.startTime, payload && payload.endTime);
        if (problem) {
            alert(problem);
            return false;
        }
        return true;
    }

    async function afterSave(kind, task) {
        const section = sections[kind];
        const app = window.AalSikke;
        if (!section || !app || !task || !task.id) return;

        try {
            if (section.box.checked) {
                const settings = section.read();
                const response = await app.apiFetch(`${app.API_BASE}/api/recurrence-rules`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        scheduleItemId: task.id,
                        frequency: 'WEEKLY',
                        intervalValue: Number(settings.every),
                        daysOfWeek: settings.days.join(','),
                        untilDate: settings.until || null
                    })
                });
                if (!response.ok) throw new Error(await messageFrom(response));
                section.rule = await response.json();
                task.recurring = true;
            } else if (kind === 'edit' && section.rule) {
                const response = await app.apiFetch(
                    `${app.API_BASE}/api/recurrence-rules/${encodeURIComponent(section.rule.id)}`,
                    { method: 'DELETE' });
                if (!response.ok && response.status !== 404) throw new Error(await messageFrom(response));
                section.rule = null;
                task.recurring = false;
            }
        } catch (error) {
            if (error && error.sessionExpired) return;
            console.error('Weekly repeat could not be saved:', error);
            alert(`The task was saved, but the weekly repeat could not be saved.\n\n${error.message}`);
        }
    }

    async function messageFrom(response) {
        try {
            const data = await response.json();
            if (data.errors) return Object.values(data.errors).join('\n');
            return data.message || data.error || `Server responded ${response.status}`;
        } catch (_) {
            return `Server responded ${response.status}`;
        }
    }

    // The edit window shows the task's current repeat
    async function loadRuleFor(section, taskId) {
        const app = window.AalSikke;
        section.rule = null;
        section.fill(null);
        if (!app || !taskId) return;
        section.loading = true;
        section.sync();
        try {
            const response = await app.apiFetch(`${app.API_BASE}/api/recurrence-rules`);
            if (!response.ok) throw new Error(`Server responded ${response.status}`);
            const rules = await response.json();
            const rule = rules.find(r => String(r.scheduleItemId) === String(taskId)) || null;
            // Ignore the answer if another task was opened meanwhile
            if (document.getElementById('edit-task-id')?.value !== taskId) return;
            section.rule = rule;
            section.loading = false;
            section.fill(rule);
        } catch (error) {
            if (!(error && error.sessionExpired)) console.error('Could not load the weekly repeat:', error);
        } finally {
            section.loading = false;
            section.sync();
        }
    }

    window.AalSikkeRepeat = { check, afterSave, labelHtml, weekdayOf, localDateOf, problemWith };

    // ---------------- set up ----------------

    document.addEventListener('DOMContentLoaded', () => {
        if (document.querySelector('.repeat-field')) return;   // already set up

        // New task form (Tasks page)
        const newStartField = document.getElementById('field-task-start');
        const taskForm = document.getElementById('task-form');
        if (newStartField && taskForm) {
            const section = buildSection('task',
                document.getElementById('task-has-start'),
                document.getElementById('task-start'));
            newStartField.after(section.field);
            sections.new = section;
            // After "+ Add task" the form is reset: clear the repeat too
            taskForm.addEventListener('reset', () => setTimeout(() => section.fill(null), 0));
        }

        // Edit task window (Tasks page and Dashboard)
        const editStartField = document.getElementById('field-edit-task-start');
        const modal = document.getElementById('task-modal');
        if (editStartField && modal) {
            const section = buildSection('edit-task',
                document.getElementById('edit-task-has-start'),
                document.getElementById('edit-task-start'));
            editStartField.after(section.field);
            sections.edit = section;

            new MutationObserver(() => {
                if (!modal.hidden) loadRuleFor(section, document.getElementById('edit-task-id')?.value || '');
            }).observe(modal, { attributes: true, attributeFilter: ['hidden'] });
        }
    });
})();