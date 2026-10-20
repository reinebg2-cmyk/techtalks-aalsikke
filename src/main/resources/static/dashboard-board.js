// Aal Sikke — Dashboard task board (card view)
//
// Task cards grouped into columns ("By time / By status / By role").
// Uses dashboard.js (loaded first) through window.AalSikke and re-renders on
// 'aalsikke:tasks-loaded'.
// Needs i18n.js loaded first (t, lang, LANGS).

document.addEventListener('DOMContentLoaded', () => {

    const app = window.AalSikke;
    if (!app) {
        return; // dashboard.js redirected to login (no token)
    }

    const boardEl = document.getElementById('task-board');
    const stateEl = document.getElementById('board-state');
    const viewTabs = document.querySelectorAll('[data-board-view]');

    if (!boardEl) {
        return;
    }

    const VIEW_KEY = 'aalsikke_board_view';

    const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY', 'PROJECT', 'QUIZ', 'LAB', 'PRESENTATION']);
    const WORK_TYPES = new Set(['SHIFT', 'MEETING', 'WORK_TASK', 'PAYMENT_DUE']);

    const PRIORITY_RANK = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };

    // Each column's visible title comes from t(titleKey) when the board is drawn.
    const VIEWS = {
        TIME: {
            keyOf: timeGroupOf,
            columns: [
                { key: 'OVERDUE', titleKey: 'tasks.group.OVERDUE' },
                { key: 'TODAY', titleKey: 'tasks.group.TODAY' },
                { key: 'UPCOMING', titleKey: 'tasks.group.UPCOMING' },
                { key: 'NO_DATE', titleKey: 'tasks.group.NO_DATE', optional: true },
                { key: 'COMPLETED', titleKey: 'tasks.group.COMPLETED' }
            ]
        },
        STATUS: {
            keyOf: statusOf,
            columns: [
                { key: 'PENDING', titleKey: 'status.PENDING' },
                { key: 'OVERDUE', titleKey: 'status.OVERDUE' },
                { key: 'COMPLETED', titleKey: 'status.COMPLETED' }
            ]
        },
        ROLE: {
            keyOf: roleOf,
            columns: [
                { key: 'STUDENT', titleKey: 'group.student' },
                { key: 'WORK', titleKey: 'group.work' },
                { key: 'PERSONAL', titleKey: 'group.personal', optional: true }
            ]
        }
    };

    let activeView = readSavedView();

    // ============================================================
    // HELPERS
    // ============================================================

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value ?? '';
        return div.innerHTML;
    }

    // Due = deadline; window-only tasks (e.g. classes) are due when the window ends.
    function taskDate(task) {
        // KAN-371: a repeating task is due at the end of its next repeat, not its first date
        const next = task.nextOccurrence;
        const value = next ? (next.end || next.start) : (task.deadline || task.endTime || task.startTime);
        if (!value) return null;
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatDue(date) {
        if (!date) return t('due.none');
        return formatAppDateTime(date);   // KAN-383
    }

    function startLabel(task) {
        // KAN-371: a repeating task starts at its next repeat
        const value = task.nextOccurrence ? task.nextOccurrence.start : task.startTime;
        if (!value) return null;
        const start = new Date(value);
        return Number.isNaN(start.getTime()) ? null : formatDue(start);
    }

    function isCompleted(task) {
        // KAN-371: a repeating task's done state is its next repeat's (that week)
        const value = task.nextOccurrence ? task.nextOccurrence.status : task.status;
        return String(value || '').toUpperCase() === 'COMPLETED';
    }

    function roleOf(task) {
        const type = String(task.itemType || '').toUpperCase();
        if (STUDENT_TYPES.has(type)) return 'STUDENT';
        if (WORK_TYPES.has(type)) return 'WORK';
        return 'PERSONAL';
    }

    function priorityOf(task) {
        const priority = String(task.priority || 'MEDIUM').toUpperCase();
        return PRIORITY_RANK[priority] ? priority : 'MEDIUM';
    }

    // Backend stores PENDING/COMPLETED; OVERDUE is derived (not done, date passed).
    function statusOf(task) {
        if (isCompleted(task)) return 'COMPLETED';
        const date = taskDate(task);
        return date && date < new Date() ? 'OVERDUE' : 'PENDING';
    }

    function timeGroupOf(task) {
        const status = statusOf(task);
        if (status !== 'PENDING') return status;
        const date = taskDate(task);
        if (!date) return 'NO_DATE';
        return date.toDateString() === new Date().toDateString() ? 'TODAY' : 'UPCOMING';
    }

    // Translated category name ("Assignment", "Exam", ...)
    function categoryLabel(type) {
        return t('cat.' + String(type).toUpperCase());
    }

    // Inside a column: overdue first, then soonest deadline, completed last.
    function compareCards(a, b) {
        const rank = task => ({ OVERDUE: 0, PENDING: 1, COMPLETED: 2 })[statusOf(task)];
        const ta = taskDate(a)?.getTime() ?? Infinity;
        const tb = taskDate(b)?.getTime() ?? Infinity;
        return (rank(a) - rank(b)) || (ta === tb ? 0 : (ta < tb ? -1 : 1));
    }

    function findTask(id) {
        return app.getTasks().find(task => String(task.id) === String(id));
    }

    function conflictPartners(task) {
        return app.getConflicts().get(String(task.id)) || [];
    }

    function firstReminder(task) {
        return Array.isArray(task.reminders) && task.reminders.length ? task.reminders[0] : null;
    }

    function readSavedView() {
        try {
            const saved = localStorage.getItem(VIEW_KEY);
            return VIEWS[saved] ? saved : 'TIME';
        } catch (_) {
            return 'TIME';
        }
    }

    function saveView(view) {
        try {
            localStorage.setItem(VIEW_KEY, view);
        } catch (_) { /* not important */ }
    }

    // ============================================================
    // RENDERING
    // ============================================================

    function cardHtml(task) {
        const completed = isCompleted(task);
        const overdue = statusOf(task) === 'OVERDUE';
        const priority = priorityOf(task);
        const partners = conflictPartners(task);
        const reminder = firstReminder(task);

        const clashTitle = t('conflict.clashes', { names: partners.map(p => p.title).join(t('common.listSep')) });
        const conflictBadge = partners.length === 0 ? '' :
            `<span class="conflict-badge" title="${escapeHtml(clashTitle)}">&#9888; ${escapeHtml(t('conflict.badge'))}</span>`;

        // The pencil reuses the Edit handler: it opens the edit popup with the reminder filled in.
        const reminderBadge = reminder && reminder.remindAt
            ? `<span class="reminder-badge" title="${escapeHtml(t('bell.reminder'))}">
                 &#128276; ${escapeHtml(formatDue(new Date(reminder.remindAt)))}
                 <button type="button" class="edit-reminder-btn task-action-edit" aria-label="${escapeHtml(t('board.editReminder'))}">&#9998;</button>
               </span>`
            : '';

        const classes = [
            'board-card',
            `priority-${priority.toLowerCase()}`,
            partners.length ? 'is-conflict' : '',
            completed ? 'is-complete' : '',
            overdue ? 'is-overdue' : ''
        ].filter(Boolean).join(' ');

        const dueText = t(overdue ? 'board.wasDue' : 'board.due', { time: formatDue(taskDate(task)) });

        return `
      <li class="${classes}" data-id="${escapeHtml(String(task.id))}">
        <div class="board-card-top">
          <p class="board-card-title">${escapeHtml(task.title)}</p>
          <div class="task-actions">
            <button type="button" class="task-menu-btn" aria-label="${escapeHtml(t('board.actionsFor', { title: task.title }))}"
                    aria-expanded="false" title="${escapeHtml(t('task.actions'))}">&#8942;</button>
            <div class="task-menu" hidden>
              <button type="button" class="task-action-edit">${escapeHtml(t('common.edit'))}</button>
              <button type="button" class="task-action-delete">${escapeHtml(t('common.delete'))}</button>
            </div>
          </div>
        </div>
        <div class="board-card-meta">
          ${task.itemType ? `<span class="board-chip">${escapeHtml(categoryLabel(task.itemType))}</span>` : ''}
          <span class="board-priority">${escapeHtml(t('board.priorityLabel', { p: t('prio.' + priority) }))}</span>
                    ${window.AalSikkeRepeat ? window.AalSikkeRepeat.labelHtml(task) : ''}
        </div>
        ${startLabel(task) ? `<p class="board-start">${escapeHtml(t('board.starts', { time: startLabel(task) }))}</p>` : ''}
        <p class="board-due">${escapeHtml(dueText)}</p>
                ${window.AalSikkeChecklist ? window.AalSikkeChecklist.progressHtml(task) : ''}
        <div class="board-card-foot">
          <label class="board-done">
            <input type="checkbox" class="task-checkbox"
                   aria-label="${escapeHtml(t('task.markComplete', { title: task.title }))}" ${completed ? 'checked' : ''}>
            ${escapeHtml(t('board.done'))}
          </label>
          ${reminderBadge}
          ${conflictBadge}
        </div>
      </li>`;
    }

    function columnHtml(column, tasks) {
        const headingId = `board-col-${column.key.toLowerCase()}`;

        return `
      <section class="board-column board-column--${column.key.toLowerCase()}" aria-labelledby="${headingId}">
        <h3 class="board-column-head" id="${headingId}">
          ${escapeHtml(t(column.titleKey))} <span class="board-count">(${tasks.length})</span>
        </h3>
        ${tasks.length === 0
            ? `<p class="board-empty">${escapeHtml(t('board.nothing'))}</p>`
            : `<ul class="board-list">${tasks.map(cardHtml).join('')}</ul>`}
      </section>`;
    }

    function showState(message, withRetry = false) {
        if (!stateEl) return;
        stateEl.innerHTML = message
            ? `${escapeHtml(message)}${withRetry ? ` <button type="button" class="link-btn" data-action="retry-board">${escapeHtml(t('common.tryAgain'))}</button>` : ''}`
            : '';
        stateEl.hidden = !message;
    }

    function syncTabs() {
        viewTabs.forEach(tab => {
            const active = tab.dataset.boardView === activeView;
            tab.classList.toggle('active', active);
            tab.setAttribute('aria-pressed', String(active));
        });
    }

    function render() {
        const tasks = app.getTasks();
        const view = VIEWS[activeView];

        if (tasks.length === 0) {
            boardEl.innerHTML = '';
            showState(t('tasks.emptyAll'));
            return;
        }

        showState('');

        const buckets = new Map(view.columns.map(column => [column.key, []]));
        tasks.forEach(task => buckets.get(view.keyOf(task))?.push(task));

        const columns = view.columns.filter(column =>
            !column.optional || buckets.get(column.key).length > 0);

        boardEl.innerHTML = columns
            .map(column => columnHtml(column, buckets.get(column.key).sort(compareCards)))
            .join('');
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    async function setStatus(task, checkbox) {
        const next = checkbox.checked ? 'COMPLETED' : 'PENDING';
        checkbox.disabled = true;

        // setTaskStatus reloads the tasks (which re-renders the board) on success.
        const ok = await app.setTaskStatus(task, next);

        if (!ok) {
            checkbox.checked = !checkbox.checked;
            checkbox.disabled = false;
        }
    }

    function closeMenus(except) {
        document.querySelectorAll('.task-menu').forEach(menu => {
            if (menu !== except) menu.hidden = true;
        });
    }

    // One listener for every card (cards are re-created on each render).
    boardEl.addEventListener('click', event => {
        const card = event.target.closest('.board-card');
        if (!card) return;

        const task = findTask(card.dataset.id);
        const menu = card.querySelector('.task-menu');
        const menuBtn = card.querySelector('.task-menu-btn');

        if (event.target.closest('.task-menu-btn')) {
            closeMenus(menu);
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

    // Click anywhere else closes open card menus
    document.addEventListener('click', event => {
        if (!event.target.closest('.task-actions')) closeMenus();
    });

    boardEl.addEventListener('change', event => {
        const checkbox = event.target.closest('.task-checkbox');
        if (!checkbox) return;
        const task = findTask(checkbox.closest('.board-card').dataset.id);
        if (task) setStatus(task, checkbox);
    });

    viewTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            activeView = VIEWS[tab.dataset.boardView] ? tab.dataset.boardView : 'TIME';
            saveView(activeView);
            syncTabs();
            if (app.getTasksState() === 'ready') safeRender();
        });
    });

    stateEl?.addEventListener('click', event => {
        if (event.target.closest('[data-action="retry-board"]')) {
            showState(t('tasks.loading'));
            app.reloadTasks();
        }
    });

    // ============================================================
    // WIRING
    // ============================================================

    function safeRender() {
        try {
            render();
        } catch (error) {
            console.error('Failed to render the task board:', error);
            boardEl.innerHTML = '';
            showState(t('board.displayFailed'), true);
        }
    }

    document.addEventListener('aalsikke:tasks-loaded', safeRender);
    document.addEventListener('aalsikke:tasks-error', () => {
        boardEl.innerHTML = '';
        showState(t('board.loadFailed'), true);
    });

    syncTabs();

    // If dashboard.js already finished loading before this ran, render now.
    if (app.getTasksState() === 'ready') {
        safeRender();
    } else if (app.getTasksState() === 'error') {
        boardEl.innerHTML = '';
        showState(t('board.loadFailed'), true);
    }
});