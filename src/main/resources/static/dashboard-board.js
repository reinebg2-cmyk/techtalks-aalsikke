// Aal Sikke — Dashboard task board (card view)
//
// Task cards grouped into columns ("By time / By status / By role").
// Uses dashboard.js (loaded first) through window.AalSikke and re-renders on
// 'aalsikke:tasks-loaded'.

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

    const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY']);
    const WORK_TYPES = new Set(['SHIFT']);

    const PRIORITY_RANK = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };

    const VIEWS = {
        TIME: {
            keyOf: timeGroupOf,
            columns: [
                { key: 'OVERDUE', title: 'Overdue' },
                { key: 'TODAY', title: 'Today' },
                { key: 'UPCOMING', title: 'Upcoming' },
                { key: 'NO_DATE', title: 'No date', optional: true },
                { key: 'COMPLETED', title: 'Completed' }
            ]
        },
        STATUS: {
            keyOf: statusOf,
            columns: [
                { key: 'PENDING', title: 'Pending' },
                { key: 'OVERDUE', title: 'Overdue' },
                { key: 'COMPLETED', title: 'Completed' }
            ]
        },
        ROLE: {
            keyOf: roleOf,
            columns: [
                { key: 'STUDENT', title: 'Student' },
                { key: 'WORK', title: 'Work' },
                { key: 'PERSONAL', title: 'Personal', optional: true }
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
        const value = task.deadline || task.endTime || task.startTime;
        if (!value) return null;
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatDue(date) {
        if (!date) return 'No deadline';
        return date.toLocaleString(undefined, {
            month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'
        });
    }

    function startLabel(task) {
        if (!task.startTime) return null;
        const start = new Date(task.startTime);
        return Number.isNaN(start.getTime()) ? null : formatDue(start);
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

    function titleCase(value) {
        const text = String(value || '').toLowerCase();
        return text.charAt(0).toUpperCase() + text.slice(1);
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

        const conflictBadge = partners.length === 0 ? '' :
            `<span class="conflict-badge" title="Clashes with ${escapeHtml(partners.map(p => p.title).join(', '))}">&#9888; Conflict</span>`;

        // The pencil reuses the Edit handler: it opens the edit popup with the reminder filled in.
        const reminderBadge = reminder && reminder.remindAt
            ? `<span class="reminder-badge" title="Reminder">
                 &#128276; ${escapeHtml(formatDue(new Date(reminder.remindAt)))}
                 <button type="button" class="edit-reminder-btn task-action-edit" aria-label="Edit reminder">&#9998;</button>
               </span>`
            : '';

        const classes = [
            'board-card',
            `priority-${priority.toLowerCase()}`,
            partners.length ? 'is-conflict' : '',
            completed ? 'is-complete' : '',
            overdue ? 'is-overdue' : ''
        ].filter(Boolean).join(' ');

        return `
      <li class="${classes}" data-id="${escapeHtml(String(task.id))}">
        <div class="board-card-top">
          <p class="board-card-title">${escapeHtml(task.title)}</p>
          <div class="task-actions">
            <button type="button" class="task-menu-btn" aria-label="Actions for ${escapeHtml(task.title)}"
                    aria-expanded="false" title="Task actions">&#8942;</button>
            <div class="task-menu" hidden>
              <button type="button" class="task-action-edit">Edit</button>
              <button type="button" class="task-action-delete">Delete</button>
            </div>
          </div>
        </div>
        <div class="board-card-meta">
          ${task.itemType ? `<span class="board-chip">${escapeHtml(titleCase(task.itemType))}</span>` : ''}
          <span class="board-priority">${titleCase(priority)} priority</span>
        </div>
        ${startLabel(task) ? `<p class="board-start">Starts ${escapeHtml(startLabel(task))}</p>` : ''}
        <p class="board-due">${overdue ? 'Was due' : 'Due'} ${escapeHtml(formatDue(taskDate(task)))}</p>
                ${window.AalSikkeChecklist ? window.AalSikkeChecklist.progressHtml(task) : ''}
        <div class="board-card-foot">
          <label class="board-done">
            <input type="checkbox" class="task-checkbox"
                   aria-label="Mark ${escapeHtml(task.title)} complete" ${completed ? 'checked' : ''}>
            Done
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
          ${column.title} <span class="board-count">(${tasks.length})</span>
        </h3>
        ${tasks.length === 0
            ? '<p class="board-empty">Nothing here</p>'
            : `<ul class="board-list">${tasks.map(cardHtml).join('')}</ul>`}
      </section>`;
    }

    function showState(message, withRetry = false) {
        if (!stateEl) return;
        stateEl.innerHTML = message
            ? `${escapeHtml(message)}${withRetry ? ' <button type="button" class="link-btn" data-action="retry-board">Try again</button>' : ''}`
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
            showState('No tasks yet. Use the + button at the top to add your first one.');
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
            showState('Loading your tasks…');
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
            showState("Couldn't display your tasks.", true);
        }
    }

    document.addEventListener('aalsikke:tasks-loaded', safeRender);
    document.addEventListener('aalsikke:tasks-error', () => {
        boardEl.innerHTML = '';
        showState("Couldn't load your tasks.", true);
    });

    syncTabs();

    // If dashboard.js already finished loading before this ran, render now.
    if (app.getTasksState() === 'ready') {
        safeRender();
    } else if (app.getTasksState() === 'error') {
        boardEl.innerHTML = '';
        showState("Couldn't load your tasks.", true);
    }
});