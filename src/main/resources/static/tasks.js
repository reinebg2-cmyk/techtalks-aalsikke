// Aal Sikke — Tasks page (KAN-259)
//
// Reuses dashboard.js (loaded first) for the task data, create form, edit modal,
// delete, and the shared shell (bell, profile menu, theme). This file only adds
// the grouped view, search and filters.
//
// dashboard.js fires 'aalsikke:tasks-loaded' after every task change (load,
// create, edit, complete, delete), so this page re-renders by itself.
// Needs i18n.js loaded first (t, lang, LANGS).

document.addEventListener('DOMContentLoaded', () => {

    const app = window.AalSikke;
    if (!app) {
        return; // dashboard.js redirected to login (no token)
    }

    const groupsEl = document.getElementById('tasks-groups');
    const stateEl = document.getElementById('tasks-state');
    const countEl = document.getElementById('tasks-count');
    const searchInput = document.getElementById('tasks-search');
    const roleSelect = document.getElementById('tasks-role-filter');
    const categorySelect = document.getElementById('tasks-category-filter');
    const prioritySelect = document.getElementById('tasks-priority-filter');
    const statusSelect = document.getElementById('tasks-status-filter');

    if (!groupsEl) {
        return;
    }

    const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY', 'PROJECT', 'QUIZ', 'LAB', 'PRESENTATION']);
    const WORK_TYPES = new Set(['SHIFT', 'MEETING', 'WORK_TASK', 'PAYMENT_DUE']);

    // Display order. Completed starts collapsed so the page stays focused.
    // The visible title comes from t('tasks.group.<key>').
    const GROUPS = [
        { key: 'OVERDUE', open: true },
        { key: 'TODAY', open: true },
        { key: 'UPCOMING', open: true },
        { key: 'NO_DATE', open: true },
        { key: 'COMPLETED', open: false }
    ];

    // Remembers which groups the user opened/closed across re-renders.
    const openGroups = new Map(GROUPS.map(group => [group.key, group.open]));

    // ============================================================
    // HELPERS (same rules as the dashboard)
    // ============================================================

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value ?? '';
        return div.innerHTML;
    }

    function taskDate(task) {
        const value = task.deadline || task.startTime;
        if (!value) return null;
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatDue(date) {
        if (!date) return t('due.none');
        return date.toLocaleString(LANGS[lang].locale, {
            month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'
        });
    }

    function isCompleted(task) {
        return String(task.status || '').toUpperCase() === 'COMPLETED';
    }

    function roleOf(task) {
        const type = String(task.itemType || '').toUpperCase();
        if (STUDENT_TYPES.has(type)) return 'STUDENT';
        if (WORK_TYPES.has(type)) return 'WORK';
        return 'PERSONAL';
    }

    // Missing priority = MEDIUM (the backend's default).
    function priorityOf(task) {
        return String(task.priority || 'MEDIUM').toUpperCase();
    }

    // Same three buckets as the dashboard's Status filter.
    function statusOf(task) {
        if (isCompleted(task)) return 'COMPLETED';
        const date = taskDate(task);
        return date && date < new Date() ? 'OVERDUE' : 'PENDING';
    }

    function groupOf(task) {
        const status = statusOf(task);
        if (status !== 'PENDING') return status; // COMPLETED or OVERDUE
        const date = taskDate(task);
        if (!date) return 'NO_DATE';
        return date.toDateString() === new Date().toDateString() ? 'TODAY' : 'UPCOMING';
    }

    // Soonest first; undated tasks last.
    function byDate(a, b) {
        const ta = taskDate(a)?.getTime() ?? Infinity;
        const tb = taskDate(b)?.getTime() ?? Infinity;
        return ta === tb ? 0 : (ta < tb ? -1 : 1);
    }

    function findTask(id) {
        return app.getTasks().find(task => String(task.id) === String(id));
    }

    function conflictPartners(task) {
        return app.getConflicts().get(String(task.id)) || [];
    }

    // ============================================================
    // FILTERING (all local, no API calls)
    // ============================================================

    function matchesFilters(task) {
        const query = (searchInput?.value || '').trim().toLowerCase();
        const role = roleSelect?.value || 'ALL';
        const category = categorySelect?.value || 'ALL';
        const priority = prioritySelect?.value || 'ALL';
        const status = statusSelect?.value || 'ALL';

        return (!query || String(task.title || '').toLowerCase().includes(query))
            && (role === 'ALL' || roleOf(task) === role)
            && (category === 'ALL' || String(task.itemType || '').toUpperCase() === category)
            && (priority === 'ALL' || priorityOf(task) === priority)
            && (status === 'ALL' || statusOf(task) === status);
    }

    // ============================================================
    // RENDERING (same card markup/classes as the dashboard)
    // ============================================================

    function cardHtml(task) {
        const completed = isCompleted(task);
        const partners = conflictPartners(task);
        const clashTitle = t('conflict.clashes', { names: partners.map(p => p.title).join(t('common.listSep')) });
        const badge = partners.length === 0 ? '' :
            `<span class="conflict-badge" title="${escapeHtml(clashTitle)}">&#9888; ${escapeHtml(t('conflict.badge'))}</span>`;
        const typeLabel = task.itemType ? t('cat.' + String(task.itemType).toUpperCase()) : '';

        return `
      <li class="task-card priority-${priorityOf(task).toLowerCase()}${partners.length ? ' is-conflict' : ''}${completed ? ' is-complete' : ''}"
          data-id="${escapeHtml(String(task.id))}">
        <input type="checkbox" class="task-checkbox"
               aria-label="${escapeHtml(t('task.markComplete', { title: task.title }))}" ${completed ? 'checked' : ''}>
        <div class="task-body">
          <p class="task-title">${escapeHtml(task.title)}</p>
          <div class="task-meta">
            <span class="priority-dot ${priorityOf(task).toLowerCase()}"></span>
            <span class="priority-label ${priorityOf(task).toLowerCase()}">${escapeHtml(t('prio.' + priorityOf(task)))}</span>
            ${typeLabel ? escapeHtml(typeLabel) + ' &middot; ' : ''}
            ${escapeHtml(t('task.dueLabel'))} ${escapeHtml(formatDue(taskDate(task)))}
                        ${window.AalSikkeRepeat ? window.AalSikkeRepeat.labelHtml(task) : ''}
            ${badge}
                        ${window.AalSikkeChecklist ? window.AalSikkeChecklist.progressHtml(task) : ''}
          </div>
        </div>
        <div class="task-actions">
          <button type="button" class="task-menu-btn" aria-label="${escapeHtml(t('task.actions'))}"
                  aria-expanded="false" title="${escapeHtml(t('task.actions'))}">&#8942;</button>
          <div class="task-menu" hidden>
            <button type="button" class="task-action-edit">${escapeHtml(t('common.edit'))}</button>
            <button type="button" class="task-action-delete">${escapeHtml(t('common.delete'))}</button>
          </div>
        </div>
      </li>`;
    }

    function showState(message) {
        if (!stateEl) return;
        stateEl.textContent = message;
        stateEl.hidden = !message;
    }

    function render() {
        const all = app.getTasks();
        const visible = all.filter(matchesFilters);

        if (countEl) {
            countEl.textContent = all.length === 0 ? '' :
                t(all.length === 1 ? 'tasks.showingOne' : 'tasks.showing', { n: visible.length, total: all.length });
        }

        if (visible.length === 0) {
            groupsEl.innerHTML = '';
            showState(all.length === 0 ? t('tasks.emptyAllPage') : t('tasks.noMatch'));
            return;
        }

        showState('');

        groupsEl.innerHTML = GROUPS.map(group => {
            const items = visible.filter(task => groupOf(task) === group.key).sort(byDate);
            if (items.length === 0) return '';

            return `
        <details class="tasks-group" data-group="${group.key}"${openGroups.get(group.key) ? ' open' : ''}>
          <summary>
            <strong>${escapeHtml(t('tasks.group.' + group.key))}</strong>
            <span class="tasks-group-count">(${items.length})</span>
          </summary>
          <ul class="task-list">${items.map(cardHtml).join('')}</ul>
        </details>`;
        }).join('');
    }

    // ============================================================
    // ACTIONS (edit/delete reuse dashboard.js; status is one PATCH)
    // ============================================================

    async function setStatus(task, checkbox) {
        const next = checkbox.checked ? 'COMPLETED' : 'PENDING';
        checkbox.disabled = true;

        try {
            const response = await app.apiFetch(
                `${app.API_BASE}/api/schedule-items/${task.id}/status`,
                {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ status: next })
                }
            );

            if (!response.ok) {
                throw new Error(`Server responded ${response.status}`);
            }

            await app.reloadTasks(); // fires 'aalsikke:tasks-loaded' -> render()

        } catch (error) {
            if (error.sessionExpired) return;
            console.error('Failed to update task status:', error);
            checkbox.checked = !checkbox.checked;
            checkbox.disabled = false;
            app.showToast(t('err.statusUpdate'), { type: 'error' });
        }
    }

    // KAN-339: download the user's tasks as an .ics file (Google Calendar / phone calendar).
    // A plain link can't send the JWT, so fetch the file and hand it to the browser as a download.
    const exportBtn = document.getElementById('export-calendar-btn');
    if (exportBtn) {
        exportBtn.addEventListener('click', async () => {
            exportBtn.disabled = true;
            try {
                const response = await app.apiFetch(`${app.API_BASE}/api/schedule-items/me/calendar.ics`);
                if (!response.ok) {
                    throw new Error(`Server responded ${response.status}`);
                }
                const url = URL.createObjectURL(await response.blob());
                const link = document.createElement('a');
                link.href = url;
                link.download = 'aalsikke.ics';
                document.body.appendChild(link);
                link.click();
                link.remove();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
            } catch (error) {
                if (error.sessionExpired) return;
                console.error('Failed to export calendar:', error);
                app.showToast(t('tasks.exportFailed'), { type: 'error' });
            } finally {
                exportBtn.disabled = false;
            }
        });
    }

    // One listener for every card (cards are re-created on each render).
    groupsEl.addEventListener('click', event => {
        const card = event.target.closest('.task-card');
        if (!card) return;

        const task = findTask(card.dataset.id);
        const menu = card.querySelector('.task-menu');
        const menuBtn = card.querySelector('.task-menu-btn');

        if (event.target.closest('.task-menu-btn')) {
            document.querySelectorAll('.task-menu').forEach(other => {
                if (other !== menu) other.hidden = true;
            });
            menu.hidden = !menu.hidden;
            menuBtn.setAttribute('aria-expanded', String(!menu.hidden));
            return;
        }

        if (event.target.closest('.task-action-edit') && task) {
            menu.hidden = true;
            app.openEditModal(task);
            return;
        }

        if (event.target.closest('.task-action-delete') && task) {
            menu.hidden = true;
            app.deleteTask(task.id);
        }
    });

    groupsEl.addEventListener('change', event => {
        const checkbox = event.target.closest('.task-checkbox');
        if (!checkbox) return;
        const task = findTask(checkbox.closest('.task-card').dataset.id);
        if (task) setStatus(task, checkbox);
    });

    // 'toggle' doesn't bubble, so listen in the capture phase.
    groupsEl.addEventListener('toggle', event => {
        const group = event.target.closest('.tasks-group');
        if (group) openGroups.set(group.dataset.group, group.open);
    }, true);

    // ============================================================
    // WIRING
    // ============================================================

    searchInput?.addEventListener('input', render);
    [roleSelect, categorySelect, prioritySelect, statusSelect].forEach(select => {
        select?.addEventListener('change', render);
    });

    document.addEventListener('aalsikke:tasks-loaded', render);
    document.addEventListener('aalsikke:tasks-error', () => {
        groupsEl.innerHTML = '';
        if (countEl) countEl.textContent = '';
        showState(t('tasks.loadFailedPage'));
    });

    // If dashboard.js already finished loading before this ran, render now.
    if (app.getTasksState() === 'ready') {
        render();
    }


    // ============================================================
    // "+" ADD TASK LINK (tasks.html#new-task)
    // The dashboard's top-bar "+" links here: open the New task form and put
    // the cursor in the title field. Also works when already on this page.
    // ============================================================

    // KAN-366: the calendar opens tasks.html?date=YYYY-MM-DD&start=HH:MM[&end=HH:MM]#new-task
    // for a free slot. Fill Start, and Due = end of the slot (in this form Due is the time block's end).
    function prefillFromCalendar() {
        const params = new URLSearchParams(window.location.search);
        const date = params.get('date');
        const start = params.get('start');
        const end = params.get('end');
        const isDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '');
        const isTime = value => /^\d{2}:\d{2}$/.test(value || '');
        if (!isDate(date) || !isTime(start)) return;   // nothing valid to fill in: plain empty form

        const set = (id, value) => {
            const input = document.getElementById(id);
            if (!input) return;
            if (input.type === 'checkbox') input.checked = value; else input.value = value;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));   // e.g. shows the Start field
        };
        set('task-has-start', true);
        set('task-start', `${date}T${start}`);
        if (isTime(end) && end > start) set('task-due', `${date}T${end}`);
    }

    // ============================================================
    // KAN-372: AI QUICK-ADD
    // Sends the sentence to /quick-parse and fills the New task form with the answer.
    // Nothing is saved here: the user reviews the form and presses "+ Add task".
    // Also runs for tasks.html?quick=...#new-task (the dashboard's quick-add box).
    // ============================================================

    const quickForm = document.getElementById('quick-add-form');
    const quickInput = document.getElementById('quick-add-input');
    const quickButton = document.getElementById('quick-add-btn');
    const quickStatus = document.getElementById('quick-add-status');

    function setField(id, value) {
        const input = document.getElementById(id);
        if (!input) return;
        if (input.type === 'checkbox') input.checked = value; else input.value = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));   // e.g. shows the Start field
    }

    // "2026-10-19T10:00:00" -> "2026-10-19T10:00" (what datetime-local inputs take)
    const toInputValue = value => (value ? String(value).slice(0, 16) : '');

    function openTaskForm() {
        const form = document.getElementById('task-form');
        if (form) form.hidden = false;
        return form;
    }

    function fillFromQuickAdd(result) {
        const form = openTaskForm();
        if (!form) return;
        form.reset();   // start clean; repeat.js clears the old repeat on reset in a setTimeout(0)
        setTimeout(() => {   // ...so fill after that has run
            setField('task-title', result.title || '');
            if (result.priority) setField('task-priority', result.priority);
            if (result.category) setField('task-category', result.category);
            setField('task-has-start', Boolean(result.start));
            if (result.start) setField('task-start', toInputValue(result.start));
            if (result.due) setField('task-due', toInputValue(result.due));
            // Repeat needs a start time; repeat.js uses MON..SUN, the API sends MONDAY..SUNDAY
            if (result.start && result.repeatDays && result.repeatDays.length && window.AalSikkeRepeat) {
                window.AalSikkeRepeat.fill('new', result.repeatDays.map(day => day.slice(0, 3)));
            }
            form.closest('.card')?.scrollIntoView({ block: 'start' });
            document.getElementById('task-title')?.focus();
        }, 0);
    }

    async function runQuickAdd(text) {
        text = (text || '').trim();
        if (!text || !quickButton) return;

        quickButton.disabled = true;
        quickInput.disabled = true;
        quickStatus.textContent = t('quick.thinking');
        try {
            const response = await app.apiFetch(`${app.API_BASE}/api/schedule-items/quick-parse`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text })
            });
            if (!response.ok) {
                // 422 = not understood / AI down, 429 = hourly limit: open the empty form either way
                quickStatus.textContent = t(response.status === 429 ? 'quick.limit' : 'quick.failed');
                openTaskForm();
                return;
            }
            fillFromQuickAdd(await response.json());
            quickStatus.textContent = t('quick.done');
            quickInput.value = '';
        } catch (error) {
            if (error.sessionExpired) return;
            console.error('Quick add failed:', error);
            quickStatus.textContent = t('quick.failed');
            openTaskForm();
        } finally {
            quickButton.disabled = false;
            quickInput.disabled = false;
        }
    }

    // After "+ Add task" (or Cancel) the form is reset: clear the quick-add message.
    // Our own form.reset() in fillFromQuickAdd runs before the message is set, so it isn't affected.
    document.getElementById('task-form')?.addEventListener('reset', () => {
        if (quickStatus) quickStatus.textContent = '';
    });

    quickForm?.addEventListener('submit', event => {
        event.preventDefault();
        runQuickAdd(quickInput.value);
    });

    // From the dashboard's box: tasks.html?quick=...#new-task
    // (read before openNewTaskFromHash() below clears the URL)
    const quickFromUrl = new URLSearchParams(window.location.search).get('quick');
    if (quickFromUrl && quickInput) {
        quickInput.value = quickFromUrl;
        runQuickAdd(quickFromUrl);
    }

    function openNewTaskFromHash() {
        if (window.location.hash !== '#new-task') return;

        const form = document.getElementById('task-form');
        const titleInput = document.getElementById('task-title');
        if (!form) return;

        form.hidden = false;
        form.closest('.card')?.scrollIntoView({ block: 'start' });
        prefillFromCalendar();
        titleInput?.focus();

        // Drop the hash (and the calendar's ?date&start&end) so a refresh or a second "+" click starts clean.
        history.replaceState(null, '', window.location.pathname);
    }

    window.addEventListener('hashchange', openNewTaskFromHash);
    openNewTaskFromHash();

});