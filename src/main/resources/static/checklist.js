// Aal Sikke — task checklist (KAN-342)
//
// 1. progressHtml(task): the small "2/5" bar used on the Tasks page and dashboard cards.
// 2. A "Checklist" section inside the Edit task window (tasks.html and dashboard.html):
//    add (Enter or Add), tick, edit text in place, move up/down, delete.
//    Every change is saved straight away through /api/schedule-items/{id}/checklist.
//    When every step is ticked, it offers to mark the task as completed.
// Uses dashboard.js (window.AalSikke) for the token, API base and task reload.
// Needs i18n.js loaded first (t).

(function () {

    // ---------------- card progress ("2/5") ----------------

    function progressHtml(task) {
        const total = Number(task && task.checklistTotal) || 0;
        if (total === 0) return '';
        const done = Math.min(Number(task.checklistDone) || 0, total);
        const percent = Math.round(done / total * 100);
        const label = t('checklist.progress', { done, total });
        return `<span class="checklist-progress${done === total ? ' is-complete' : ''}"`
            + ` title="${label}" aria-label="${label}">`
            + `<span class="checklist-bar" aria-hidden="true"><span style="width:${percent}%"></span></span>`
            + `${done}/${total}</span>`;
    }

    window.AalSikkeChecklist = { progressHtml };

    // ---------------- edit-window section ----------------

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    document.addEventListener('DOMContentLoaded', () => {

        const app = window.AalSikke;
        const modal = document.getElementById('task-modal');
        const taskIdInput = document.getElementById('edit-task-id');
        const description = document.getElementById('edit-task-description');
        if (!app || !modal || !taskIdInput || !description) return;
        if (document.querySelector('.checklist-field')) return;   // already set up

        // ----- markup -----
        const field = el('div', 'field checklist-field');
        const heading = el('p', 'checklist-heading', t('checklist.title'));
        heading.id = 'checklist-heading';
        const summary = el('span', 'checklist-summary');
        heading.append(' ', summary);

        const list = el('ul', 'checklist-list');
        list.setAttribute('aria-labelledby', 'checklist-heading');

        const addRow = el('div', 'checklist-add');
        const newInput = el('input');
        newInput.type = 'text';
        newInput.id = 'checklist-new';
        newInput.maxLength = 200;
        newInput.placeholder = t('checklist.addPlaceholder');
        newInput.setAttribute('aria-label', t('checklist.newStep'));
        const addButton = el('button', 'btn-secondary checklist-add-btn', t('checklist.add'));
        addButton.type = 'button';
        addRow.append(newInput, addButton);

        const allDone = el('div', 'checklist-all-done');
        allDone.hidden = true;
        const allDoneText = el('span', '', t('checklist.allDone') + ' ');
        const completeButton = el('button', 'link-btn checklist-complete-btn', t('checklist.markComplete'));
        completeButton.type = 'button';
        allDone.append(allDoneText, completeButton);

        const message = el('p', 'checklist-message');
        message.setAttribute('role', 'status');
        message.setAttribute('aria-live', 'polite');

        field.append(heading, list, addRow, allDone, message);
        description.closest('.field').after(field);

        // ----- state -----
        let taskId = '';
        let items = [];
        let changed = false;   // reload the task cards when the window closes

        const base = () => `${app.API_BASE}/api/schedule-items/${encodeURIComponent(taskId)}/checklist`;

        async function call(url, options = {}) {
            const response = await app.apiFetch(url, {
                ...options,
                headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
            });
            if (response.status === 204) return null;
            let data = null;
            try { data = await response.json(); } catch (_) { /* no body */ }
            if (response.status === 403) throw new Error(t('checklist.notYours'));
            if (!response.ok) throw new Error((data && data.message) || t('err.server', { status: response.status }));
            return data;
        }

        function say(text, isError = false) {
            message.textContent = text;
            message.classList.toggle('is-error', isError);
        }

        function fail(error) {
            if (error && error.sessionExpired) return;
            console.error('Checklist error:', error);
            say(error.message || t('err.generic'), true);
        }

        // ----- rendering -----
        function render(focus) {
            list.replaceChildren();
            items.forEach((item, index) => list.append(row(item, index)));

            const done = items.filter(i => i.done).length;
            summary.textContent = items.length ? `${done}/${items.length}` : '';

            const status = document.getElementById('edit-task-status');
            const taskCompleted = status && status.value === 'COMPLETED';
            allDone.hidden = !(items.length > 0 && done === items.length && !taskCompleted);

            if (focus) {
                // Keep keyboard focus on the same step; if that button is now disabled
                // (moved to the top/bottom), use the other arrow or the step's text.
                const rowEl = list.querySelector(`[data-item-id="${focus.id}"]`);
                const target = rowEl && [`.${focus.control}`, '.checklist-up', '.checklist-down', '.checklist-text']
                    .map(selector => rowEl.querySelector(selector))
                    .find(control => control && !control.disabled);
                (target || newInput).focus();
            }
        }

        function row(item, index) {
            const li = el('li', `checklist-item${item.done ? ' is-done' : ''}`);
            li.dataset.itemId = item.id;

            const box = el('input', 'checklist-done');
            box.type = 'checkbox';
            box.checked = item.done;
            box.setAttribute('aria-label', t('checklist.doneAria', { text: item.text }));
            box.addEventListener('change', () => toggle(item, box.checked));

            const text = el('input', 'checklist-text');
            text.type = 'text';
            text.maxLength = 200;
            text.value = item.text;
            text.setAttribute('aria-label', t('checklist.stepN', { n: index + 1 }));
            text.addEventListener('keydown', event => {
                if (event.key === 'Enter') { event.preventDefault(); text.blur(); }
                if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); text.value = item.text; text.blur(); }
            });
            text.addEventListener('blur', () => rename(item, text));

            const up = el('button', 'checklist-icon checklist-up', '↑');
            up.type = 'button';
            up.disabled = index === 0;
            up.setAttribute('aria-label', t('checklist.moveUp', { text: item.text }));
            up.addEventListener('click', () => move(index, -1, 'checklist-up'));

            const down = el('button', 'checklist-icon checklist-down', '↓');
            down.type = 'button';
            down.disabled = index === items.length - 1;
            down.setAttribute('aria-label', t('checklist.moveDown', { text: item.text }));
            down.addEventListener('click', () => move(index, 1, 'checklist-down'));

            const remove = el('button', 'checklist-icon checklist-remove', '✕');
            remove.type = 'button';
            remove.setAttribute('aria-label', t('checklist.delete', { text: item.text }));
            remove.addEventListener('click', () => removeItem(item));

            li.append(box, text, up, down, remove);
            return li;
        }

        // ----- actions -----
        async function load() {
            items = [];
            render();
            say('');
            if (!taskId) return;
            say(t('checklist.loading'));
            try {
                items = await call(base());
                say('');
                render();
            } catch (error) { fail(error); }
        }

        async function add() {
            const text = newInput.value.trim();
            if (!text) { say(t('checklist.typeFirst'), true); return; }
            addButton.disabled = true;
            try {
                const item = await call(base(), { method: 'POST', body: JSON.stringify({ text }) });
                items.push(item);
                changed = true;
                newInput.value = '';
                say(t('checklist.added', { text: item.text }));
                render();
                newInput.focus();
            } catch (error) { fail(error); } finally { addButton.disabled = false; }
        }

        async function toggle(item, done) {
            try {
                const updated = await call(`${base()}/${item.id}`, { method: 'PATCH', body: JSON.stringify({ done }) });
                Object.assign(item, updated);
                changed = true;
                say('');
                render({ id: item.id, control: 'checklist-done' });
            } catch (error) { fail(error); render(); }
        }

        async function rename(item, input) {
            const text = input.value.trim();
            if (text === item.text) { input.value = item.text; return; }
            if (!text) { input.value = item.text; say(t('checklist.needsText'), true); return; }
            try {
                const updated = await call(`${base()}/${item.id}`, { method: 'PATCH', body: JSON.stringify({ text }) });
                Object.assign(item, updated);
                changed = true;
                say(t('checklist.renamed'));
                render();
            } catch (error) { fail(error); input.value = item.text; }
        }

        async function move(index, step, control) {
            const target = index + step;
            if (target < 0 || target >= items.length) return;
            const order = items.map(i => i.id);
            [order[index], order[target]] = [order[target], order[index]];
            const movedId = items[index].id;
            try {
                items = await call(`${base()}/order`, { method: 'PUT', body: JSON.stringify({ itemIds: order }) });
                changed = true;
                say('');
                render({ id: movedId, control });
            } catch (error) { fail(error); }
        }

        async function removeItem(item) {
            try {
                await call(`${base()}/${item.id}`, { method: 'DELETE' });
                items = items.filter(i => i.id !== item.id);
                changed = true;
                say(t('checklist.deleted', { text: item.text }));
                render();
                newInput.focus();
            } catch (error) { fail(error); }
        }

        async function completeTask() {
            completeButton.disabled = true;
            try {
                const ok = await app.setTaskStatus({ id: taskId }, 'COMPLETED');
                if (ok) {
                    const status = document.getElementById('edit-task-status');
                    if (status) status.value = 'COMPLETED';
                    say(t('checklist.taskCompleted'));
                    render();
                }
            } finally { completeButton.disabled = false; }
        }

        newInput.addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); add(); }
        });
        addButton.addEventListener('click', add);
        completeButton.addEventListener('click', completeTask);

        // Open / close of the Edit task window (dashboard.js shows and hides it)
        new MutationObserver(() => {
            if (!modal.hidden) {
                taskId = taskIdInput.value;
                changed = false;
                newInput.value = '';
                load();
            } else {
                if (changed && typeof app.reloadTasks === 'function') app.reloadTasks();
                changed = false;
                taskId = '';
            }
        }).observe(modal, { attributes: true, attributeFilter: ['hidden'] });
    });
})();