// Aal Sikke — Tasks page (KAN-259)
//
// Reuses dashboard.js (loaded first) for the task data, create form, edit modal,
// delete, and the shared shell (bell, profile menu, theme). This file only adds
// the grouped view, search and filters.
//
// dashboard.js fires 'aalsikke:tasks-loaded' after every task change (load,
// create, edit, complete, delete), so this page re-renders by itself.

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

    const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY']);
    const WORK_TYPES = new Set(['SHIFT']);

    // Display order. Completed starts collapsed so the page stays focused.
    const GROUPS = [
        { key: 'OVERDUE', title: 'Overdue', open: true },
        { key: 'TODAY', title: 'Today', open: true },
        { key: 'UPCOMING', title: 'Upcoming', open: true },
        { key: 'NO_DATE', title: 'No date', open: true },
        { key: 'COMPLETED', title: 'Completed', open: false }
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
        if (!date) return 'No deadline';
        return date.toLocaleString(undefined, {
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
        const badge = partners.length === 0 ? '' :
            `<span class="conflict-badge" title="Clashes with ${escapeHtml(partners.map(p => p.title).join(', '))}">&#9888; Conflict</span>`;

        return `
      <li class="task-card${partners.length ? ' is-conflict' : ''}${completed ? ' is-complete' : ''}"
          data-id="${escapeHtml(String(task.id))}">
        <input type="checkbox" class="task-checkbox"
               aria-label="Mark ${escapeHtml(task.title)} complete" ${completed ? 'checked' : ''}>
        <div class="task-body">
          <p class="task-title">${escapeHtml(task.title)}</p>
          <div class="task-meta">
            <span class="priority-dot ${priorityOf(task).toLowerCase()}"></span>
            ${task.itemType ? escapeHtml(task.itemType) + ' &middot; ' : ''}
            Due ${escapeHtml(formatDue(taskDate(task)))}
            ${badge}
          </div>
        </div>
        <div class="task-actions">
          <button type="button" class="task-menu-btn" aria-label="Task actions"
                  aria-expanded="false" title="Task actions">&#8942;</button>
          <div class="task-menu" hidden>
            <button type="button" class="task-action-edit">Edit</button>
            <button type="button" class="task-action-delete">Delete</button>
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
                `Showing ${visible.length} of ${all.length} task${all.length === 1 ? '' : 's'}`;
        }

        if (visible.length === 0) {
            groupsEl.innerHTML = '';
            showState(all.length === 0
                ? 'No tasks yet. Add your first one above.'
                : 'No tasks match these filters.');
            return;
        }

        showState('');

        groupsEl.innerHTML = GROUPS.map(group => {
            const items = visible.filter(task => groupOf(task) === group.key).sort(byDate);
            if (items.length === 0) return '';

            return `
        <details class="tasks-group" data-group="${group.key}"${openGroups.get(group.key) ? ' open' : ''}>
          <summary>
            <strong>${group.title}</strong>
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
            alert('Could not update the task status. Please try again.');
        }
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
        showState("Couldn't load your tasks. Please refresh the page.");
    });

    // If dashboard.js already finished loading before this ran, render now.
    if (app.getTasksState() === 'ready') {
        render();
    }
});