// Aal Sikke — Tasks page
//
// Grouped task list (Overdue / Today / Upcoming / No date / Completed) with
// search and filters. Uses dashboard.js (loaded first) through window.AalSikke
// and re-renders on 'aalsikke:tasks-loaded'.

document.addEventListener('DOMContentLoaded', () => {

  const app = window.AalSikke;
  if (!app) return; // dashboard.js redirected to login

  const groupsEl = document.getElementById('tasks-groups');
  const stateEl = document.getElementById('tasks-state');
  const countEl = document.getElementById('tasks-count');

  const searchInput = document.getElementById('tasks-search');
  const roleFilter = document.getElementById('tasks-role-filter');
  const categoryFilter = document.getElementById('tasks-category-filter');
  const priorityFilter = document.getElementById('tasks-priority-filter');
  const statusFilter = document.getElementById('tasks-status-filter');

  if (!groupsEl) return;

  const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY']);
  const WORK_TYPES = new Set(['SHIFT']);

  const GROUPS = [
    { key: 'OVERDUE', title: 'Overdue' },
    { key: 'TODAY', title: 'Today' },
    { key: 'UPCOMING', title: 'Upcoming' },
    { key: 'NO_DATE', title: 'No date' },
    { key: 'COMPLETED', title: 'Completed' }
  ];

  // ============================================================
  // HELPERS
  // ============================================================

  function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value ?? '';
    return div.innerHTML;
  }

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

  function titleCase(value) {
    const text = String(value || '').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  function isCompleted(task) {
    return String(task.status || '').toUpperCase() === 'COMPLETED';
  }

  function statusOf(task) {
    if (isCompleted(task)) return 'COMPLETED';
    const date = taskDate(task);
    return date && date < new Date() ? 'OVERDUE' : 'PENDING';
  }

  function groupOf(task) {
    const status = statusOf(task);
    if (status !== 'PENDING') return status;
    const date = taskDate(task);
    if (!date) return 'NO_DATE';
    return date.toDateString() === new Date().toDateString() ? 'TODAY' : 'UPCOMING';
  }

  function roleOf(task) {
    const type = String(task.itemType || '').toUpperCase();
    if (STUDENT_TYPES.has(type)) return 'STUDENT';
    if (WORK_TYPES.has(type)) return 'WORK';
    return 'PERSONAL';
  }

  function priorityOf(task) {
    return String(task.priority || 'MEDIUM').toUpperCase();
  }

  function firstReminder(task) {
    return Array.isArray(task.reminders) && task.reminders.length ? task.reminders[0] : null;
  }

  function findTask(id) {
    return app.getTasks().find(task => String(task.id) === String(id));
  }

  function compareTasks(a, b) {
    const ta = taskDate(a)?.getTime() ?? Infinity;
    const tb = taskDate(b)?.getTime() ?? Infinity;
    return ta === tb ? 0 : (ta < tb ? -1 : 1);
  }

  // ============================================================
  // FILTERING
  // ============================================================

  function matchesFilters(task) {
    const query = (searchInput?.value || '').trim().toLowerCase();
    if (query && !String(task.title || '').toLowerCase().includes(query)) return false;

    const role = roleFilter?.value || 'ALL';
    if (role !== 'ALL' && roleOf(task) !== role) return false;

    const category = categoryFilter?.value || 'ALL';
    if (category !== 'ALL' && String(task.itemType || '').toUpperCase() !== category) return false;

    const priority = priorityFilter?.value || 'ALL';
    if (priority !== 'ALL' && priorityOf(task) !== priority) return false;

    const status = statusFilter?.value || 'ALL';
    if (status !== 'ALL' && statusOf(task) !== status) return false;

    return true;
  }

  // ============================================================
  // RENDERING
  // ============================================================

  function rowHtml(task) {
    const completed = isCompleted(task);
    const overdue = statusOf(task) === 'OVERDUE';
    const priority = priorityOf(task);
    const partners = app.getConflicts().get(String(task.id)) || [];
    const reminder = firstReminder(task);

    const reminderBadge = reminder && reminder.remindAt
      ? `<span class="reminder-badge" title="Reminder">&#128276; ${escapeHtml(formatDue(new Date(reminder.remindAt)))}
           <button type="button" class="edit-reminder-btn" data-action="edit" aria-label="Edit reminder">&#9998;</button></span>`
      : '';

    const conflictBadge = partners.length
      ? `<span class="conflict-badge" title="Clashes with ${escapeHtml(partners.map(p => p.title).join(', '))}">&#9888; Conflict</span>`
      : '';

    return `
      <li class="task-row priority-${priority.toLowerCase()} ${completed ? 'is-complete' : ''} ${overdue ? 'is-overdue' : ''}"
          data-id="${escapeHtml(String(task.id))}">
        <label class="task-row-check">
          <input type="checkbox" class="task-checkbox" aria-label="Mark ${escapeHtml(task.title)} complete" ${completed ? 'checked' : ''}>
        </label>
        <div class="task-row-main">
          <p class="task-row-title" style="${completed ? 'text-decoration:line-through;opacity:.65;' : ''}">${escapeHtml(task.title)}</p>
          <p class="task-row-meta">
            ${task.itemType ? `<span class="board-chip">${escapeHtml(titleCase(task.itemType))}</span>` : ''}
            <span>${titleCase(priority)} priority</span>
            <span>${overdue ? 'Was due' : 'Due'} ${escapeHtml(formatDue(taskDate(task)))}</span>
            ${reminderBadge}
            ${conflictBadge}
          </p>
        </div>
        <div class="task-row-actions">
          <button type="button" class="btn-secondary" data-action="edit">Edit</button>
          <button type="button" class="btn-secondary" data-action="delete">Delete</button>
        </div>
      </li>`;
  }

  function render() {
    const all = app.getTasks();
    const visible = all.filter(matchesFilters);

    if (countEl) {
      countEl.textContent = `${visible.length} of ${all.length} task${all.length === 1 ? '' : 's'}`;
    }

    if (all.length === 0) {
      groupsEl.innerHTML = '';
      showState('No tasks yet. Press "+ New task" above to add your first one.');
      return;
    }

    if (visible.length === 0) {
      groupsEl.innerHTML = '';
      showState('No tasks match your search or filters.');
      return;
    }

    showState('');

    const buckets = new Map(GROUPS.map(group => [group.key, []]));
    visible.forEach(task => buckets.get(groupOf(task))?.push(task));

    groupsEl.innerHTML = GROUPS
      .filter(group => buckets.get(group.key).length > 0)
      .map(group => {
        const tasks = buckets.get(group.key).sort(compareTasks);
        return `
          <section class="task-group task-group--${group.key.toLowerCase()}">
            <h3 class="task-group-head">${group.title} <span class="board-count">(${tasks.length})</span></h3>
            <ul class="task-group-list" style="list-style:none;padding:0;margin:0;">${tasks.map(rowHtml).join('')}</ul>
          </section>`;
      })
      .join('');
  }

  function showState(message, withRetry = false) {
    if (!stateEl) return;
    stateEl.innerHTML = message
      ? `${escapeHtml(message)}${withRetry ? ' <button type="button" class="link-btn" data-action="retry">Try again</button>' : ''}`
      : '';
    stateEl.hidden = !message;
  }

  function safeRender() {
    try {
      render();
    } catch (error) {
      console.error('Failed to render tasks:', error);
      groupsEl.innerHTML = '';
      showState("Couldn't display your tasks.", true);
    }
  }

  // ============================================================
  // EVENTS
  // ============================================================

  groupsEl.addEventListener('click', event => {
    const row = event.target.closest('.task-row');
    if (!row) return;
    const task = findTask(row.dataset.id);
    if (!task) return;

    const actionBtn = event.target.closest('[data-action]');
    if (!actionBtn) return;

    if (actionBtn.dataset.action === 'edit') app.openEditModal(task);
    if (actionBtn.dataset.action === 'delete') app.deleteTask(task.id);
  });

  groupsEl.addEventListener('change', async event => {
    const checkbox = event.target.closest('.task-checkbox');
    if (!checkbox) return;

    const task = findTask(checkbox.closest('.task-row').dataset.id);
    if (!task) return;

    checkbox.disabled = true;
    const ok = await app.setTaskStatus(task, checkbox.checked ? 'COMPLETED' : 'PENDING');
    if (!ok) {
      checkbox.checked = !checkbox.checked;
      checkbox.disabled = false;
    }
  });

  [roleFilter, categoryFilter, priorityFilter, statusFilter].forEach(select => {
    select?.addEventListener('change', safeRender);
  });
  searchInput?.addEventListener('input', safeRender);

  stateEl?.addEventListener('click', event => {
    if (event.target.closest('[data-action="retry"]')) {
      showState('Loading your tasks...');
      app.reloadTasks();
    }
  });

  document.addEventListener('aalsikke:tasks-loaded', safeRender);
  document.addEventListener('aalsikke:tasks-error', () => {
    groupsEl.innerHTML = '';
    showState("Couldn't load your tasks.", true);
  });

  if (app.getTasksState() === 'ready') safeRender();
  else if (app.getTasksState() === 'error') showState("Couldn't load your tasks.", true);
});