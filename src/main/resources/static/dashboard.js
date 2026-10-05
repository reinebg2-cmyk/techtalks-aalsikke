// Aal Sikke — Dashboard
// Connected to the current Spring Boot backend.
//
// Schedule endpoints:
// GET    /api/schedule-items/me
// POST   /api/schedule-items
// PUT    /api/schedule-items/{id}
// DELETE /api/schedule-items/{id}
//
// Profile endpoints:
// GET    /api/auth/me
// PUT    /api/auth/profile
//
// Reminder endpoints:
// POST   /api/reminders
// PUT    /api/reminders/{id}
// DELETE /api/reminders/{id}

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token';

document.addEventListener('DOMContentLoaded', () => {

  // ============================================================
  // AUTHENTICATION
  // ============================================================

  const token = localStorage.getItem(TOKEN_KEY);

  if (!token) {
    window.location.href = 'login.html';
    return;
  }

  function authHeaders() {
    return { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` };
  }

  function redirectToLogin() {
    localStorage.removeItem(TOKEN_KEY);
    window.location.href = 'login.html';
  }

  async function apiFetch(url, options = {}) {
    const headers = {
      ...authHeaders(),
      ...(options.headers || {})
    };

    const response = await fetch(url, {
      ...options,
      headers
    });

    if (response.status === 401) {
      redirectToLogin();
      const expired = new Error('Your session has expired.');
      expired.sessionExpired = true;
      throw expired;
    }

    return response;
  }

  // ============================================================
  // DOM ELEMENTS
  // ============================================================

  const taskList = document.getElementById('task-list');
  const emptyState = document.getElementById('empty-state');

  const toggleFormBtn = document.getElementById('toggle-form-btn');
  const cancelTaskBtn = document.getElementById('cancel-task-btn');
  const taskForm = document.getElementById('task-form');
  const saveTaskBtn = document.getElementById('save-task-btn');

  const filterTabs = document.querySelectorAll('.filter-tab');

  const roleFilterSelect = document.getElementById('role-filter');
  const statusFilterSelect = document.getElementById('status-filter');
  const sortSelect = document.getElementById('sort-select');
  const taskCount = document.getElementById('task-count');

  const avatarBtn = document.getElementById('avatar-btn');
  const profileDropdown = document.getElementById('profile-dropdown');
  const editProfileLink = document.getElementById('edit-profile-link');

  // Edit task modal & fields
  const taskModal = document.getElementById('task-modal');
  const editTaskForm = document.getElementById('edit-task-form');

  const editTaskId = document.getElementById('edit-task-id');
  const editTaskTitle = document.getElementById('edit-task-title');
  const editTaskDescription = document.getElementById('edit-task-description');
  const editTaskDeadline = document.getElementById('edit-task-deadline');
  const editTaskPriority = document.getElementById('edit-task-priority');
  const editTaskCategory = document.getElementById('edit-task-category');
  const editTaskStatus = document.getElementById('edit-task-status');

  const editTaskHasStart = document.getElementById('edit-task-has-start');
  const editTaskStart = document.getElementById('edit-task-start');
  const editTaskStartField = document.getElementById('field-edit-task-start');

  // Edit Task Reminder fields
  const editTaskWantsReminder = document.getElementById('edit-task-wants-reminder');
  const editTaskReminderField = document.getElementById('field-edit-task-reminder');
  const editTaskReminderAt = document.getElementById('edit-task-reminder-at');
  const editReminderId = document.getElementById('edit-reminder-id');

  const closeTaskBtn = document.getElementById('close-task-modal');
  const cancelEditBtn = document.getElementById('cancel-edit-btn');
  const updateTaskBtn = document.getElementById('update-task-btn');

  // ============================================================
  // STATE
  // ============================================================

  let allTasks = [];
  let conflictPartnersByTaskId = new Map();

  let activeFilter = 'all';
  let activeRoleFilter = 'ALL';
  let activeStatusFilter = 'ALL';
  let activeSort = 'DEFAULT';
  let currentProfile = null;

  const studentTypes = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY']);
  const workTypes = new Set(['SHIFT']);

  // ============================================================
  // BASIC HELPERS
  // ============================================================

  function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value ?? '';
    return div.innerHTML;
  }

  function formatDue(dateStr) {
    if (!dateStr) return 'No deadline';
    const date = new Date(dateStr);
    if (Number.isNaN(date.getTime())) return 'Invalid date';

    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit'
    });
  }

  function toDateTimeLocal(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    if (Number.isNaN(date.getTime())) return '';

    const offset = date.getTimezoneOffset();
    const localDate = new Date(date.getTime() - offset * 60000);
    return localDate.toISOString().slice(0, 16);
  }

  function toISOStringFromLocal(value) {
    if (!value) return null;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    return date.toISOString();
  }

  function getTaskDate(task) {
    return task.deadline || task.endTime || task.startTime;
  }

  let toastTimer = null;
  function showToast(message) {
    const toast = document.getElementById('toast');
    if (!toast) return;

    toast.textContent = message;
    toast.classList.add('is-visible');

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toast.classList.remove('is-visible');
    }, 4000);
  }

  async function readJsonSafely(response) {
    try {
      return await response.json();
    } catch (_) {
      return null;
    }
  }

  // ============================================================
  // REMINDER BADGE & EDIT HANDLER
  // ============================================================

  function renderReminderBadge(task) {
    const existingReminder = Array.isArray(task.reminders) && task.reminders.length > 0
      ? task.reminders[0]
      : null;

    if (!existingReminder) return '';

    return `
      <span class="reminder-badge" title="Reminder set for ${formatDue(existingReminder.remindAt)}">
        🔔 ${formatDue(existingReminder.remindAt)}
        <button type="button"
                class="edit-reminder-btn"
                data-action="edit-reminder"
                data-task-id="${task.id}"
                data-reminder-id="${existingReminder.id}"
                aria-label="Edit reminder">
          ✏️
        </button>
      </span>
    `;
  }

  document.getElementById('task-list')?.addEventListener('click', event => {
    const editBtn = event.target.closest('[data-action="edit-reminder"]');
    if (!editBtn) return;

    event.stopPropagation();
    const taskId = editBtn.dataset.taskId;
    const task = allTasks.find(t => String(t.id) === String(taskId));

    if (task) {
      openEditModal(task);
    }
  });

  // ============================================================
  // REMINDER API HELPERS
  // ============================================================

  async function createReminderFor(scheduleItemId, remindAt) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scheduleItemId, remindAt })
      });

      if (!response.ok) {
        const data = await readJsonSafely(response);
        showToast((data && data.message) || 'Task saved, but reminder could not be set.');
      }
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to create reminder:', error);
      showToast('Task saved, but reminder could not be set.');
    }
  }

  async function updateReminderFor(reminderId, remindAt) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders/${reminderId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ remindAt })
      });

      if (!response.ok) {
        const data = await readJsonSafely(response);
        showToast((data && data.message) || 'Task updated, but reminder could not be updated.');
      }
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to update reminder:', error);
      showToast('Task updated, but reminder could not be updated.');
    }
  }

  async function deleteReminderFor(reminderId) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders/${reminderId}`, {
        method: 'DELETE'
      });

      if (!response.ok && response.status !== 204) {
        console.error('Failed to delete reminder:', response.status);
      }
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to delete reminder:', error);
    }
  }

  // ============================================================
  // TIME WINDOW & REMINDER FIELD TOGGLES
  // ============================================================

  const WINDOW_TYPES = new Set(['CLASS', 'SHIFT', 'STUDY']);

  function setupStartToggle(checkbox, startField, categorySelect) {
    if (!checkbox || !startField) return null;

    let userChose = false;
    const sync = () => { startField.hidden = !checkbox.checked; };

    checkbox.addEventListener('change', () => {
      userChose = true;
      sync();
    });

    if (categorySelect) {
      categorySelect.addEventListener('change', () => {
        if (!userChose) {
          checkbox.checked = WINDOW_TYPES.has(categorySelect.value);
          sync();
        }
      });
    }

    return {
      presetFromCategory() {
        userChose = false;
        checkbox.checked = Boolean(categorySelect && WINDOW_TYPES.has(categorySelect.value));
        sync();
      },
      set(checked) {
        userChose = true;
        checkbox.checked = checked;
        sync();
      },
      isChecked: () => checkbox.checked
    };
  }

  const createStartToggle = setupStartToggle(
    document.getElementById('task-has-start'),
    document.getElementById('field-task-start'),
    document.getElementById('task-category')
  );
  createStartToggle?.presetFromCategory();

  // Create form reminder opt-in
  const taskWantsReminder = document.getElementById('task-wants-reminder');
  const taskReminderField = document.getElementById('field-task-reminder');
  const taskReminderAt = document.getElementById('task-reminder-at');

  function syncReminderField() {
    if (taskWantsReminder && taskReminderField) {
      taskReminderField.hidden = !taskWantsReminder.checked;
    }
  }

  taskWantsReminder?.addEventListener('change', syncReminderField);
  syncReminderField();

  // Edit form reminder opt-in
  const editStartToggle = setupStartToggle(
    editTaskHasStart, editTaskStartField, editTaskCategory
  );

  function syncEditReminderField() {
    if (editTaskWantsReminder && editTaskReminderField) {
      editTaskReminderField.hidden = !editTaskWantsReminder.checked;
    }
  }

  editTaskWantsReminder?.addEventListener('change', syncEditReminderField);

  // ============================================================
  // NEW TASK & EDIT TASK MODAL HANDLERS
  // ============================================================

  function setupCreateTaskButton() {
    const newTaskBtn = document.querySelector('.task-create-card button') ||
                       document.getElementById('new-task-btn') ||
                       toggleFormBtn;

    const createCardForm = document.querySelector('.task-create-card form') ||
                           taskForm;

    if (newTaskBtn) {
      newTaskBtn.addEventListener('click', (e) => {
        e.preventDefault();
        if (createCardForm) {
          createCardForm.hidden = !createCardForm.hidden;
          if (!createCardForm.hidden) {
            createCardForm.querySelector('input')?.focus();
          }
        } else if (taskModal) {
          taskModal.hidden = false;
          taskModal.querySelector('input')?.focus();
        }
      });
    }
  }

  function openEditModal(task) {
    if (!taskModal || !task) return;

    if (editTaskId) editTaskId.value = task.id;
    if (editTaskTitle) editTaskTitle.value = task.title || '';
    if (editTaskDescription) editTaskDescription.value = task.description || '';
    if (editTaskPriority) editTaskPriority.value = task.priority || 'MEDIUM';
    if (editTaskCategory) editTaskCategory.value = task.itemType || 'PERSONAL';
    if (editTaskStatus) editTaskStatus.value = task.status || 'PENDING';

    const deadline = task.deadline || task.endTime || '';
    if (editTaskDeadline) editTaskDeadline.value = toDateTimeLocal(deadline);

    const hasStart = Boolean(task.startTime);
    editStartToggle?.set(hasStart);
    if (editTaskStart) editTaskStart.value = hasStart ? toDateTimeLocal(task.startTime) : '';

    // Populate existing reminder into edit form
    const existingReminder = Array.isArray(task.reminders) && task.reminders.length > 0
      ? task.reminders[0]
      : null;

    if (editTaskWantsReminder) {
      editTaskWantsReminder.checked = Boolean(existingReminder);
    }

    if (editTaskReminderAt) {
      editTaskReminderAt.value = existingReminder && existingReminder.remindAt
        ? toDateTimeLocal(existingReminder.remindAt)
        : '';
    }

    if (editReminderId) {
      editReminderId.value = existingReminder ? existingReminder.id : '';
    }

    syncEditReminderField();
    taskModal.hidden = false;
  }

  function closeEditModal() {
    if (taskModal) {
      taskModal.hidden = true;
      if (editTaskForm) editTaskForm.reset();
      if (editReminderId) editReminderId.value = '';
    }
  }

  closeTaskBtn?.addEventListener('click', closeEditModal);
  cancelEditBtn?.addEventListener('click', closeEditModal);

  // Edit task submit listener
  editTaskForm?.addEventListener('submit', async event => {
    event.preventDefault();

    const id = editTaskId?.value;
    if (!id) return;

    const title = editTaskTitle?.value.trim();
    if (!title) {
      alert('Please enter a task title.');
      return;
    }

    const deadlineVal = editTaskDeadline?.value;
    const deadline = toISOStringFromLocal(deadlineVal);

    if (!deadline) {
      alert('Please select a due time or deadline.');
      return;
    }

    let startTime = null;
    let endTime = null;

    if (editStartToggle?.isChecked()) {
      const startVal = editTaskStart?.value;
      startTime = toISOStringFromLocal(startVal);

      if (!startTime) {
        alert('Please select a start time, or untick "Has a start time".');
        return;
      }

      if (new Date(startTime) >= new Date(deadline)) {
        alert('Start time must be before the due time.');
        return;
      }

      endTime = deadline;
    }

    // Reminder processing validation
    const wantsReminder = Boolean(editTaskWantsReminder?.checked);
    const existingReminderId = editReminderId?.value || null;
    let remindAt = null;

    if (wantsReminder) {
      remindAt = toISOStringFromLocal(editTaskReminderAt?.value || '');

      if (!remindAt) {
        alert('Please choose when to be reminded, or untick "Remind me".');
        return;
      }

      if (new Date(remindAt) <= new Date()) {
        alert('The reminder must be in the future.');
        return;
      }

      if (new Date(remindAt) > new Date(deadline)) {
        alert('The reminder cannot be after the due time.');
        return;
      }
    }

    const payload = {
      title,
      description: editTaskDescription?.value.trim() || null,
      itemType: editTaskCategory?.value || 'PERSONAL',
      priority: editTaskPriority?.value || 'MEDIUM',
      status: editTaskStatus?.value || 'PENDING',
      startTime,
      endTime,
      deadline
    };

    if (updateTaskBtn) updateTaskBtn.disabled = true;

    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const errorData = await readJsonSafely(response);
        throw new Error((errorData && errorData.message) || `Failed to update task: ${response.status}`);
      }

      const updatedTask = await response.json();

      // Handle Reminder CRUD operations based on choice
      if (wantsReminder && remindAt) {
        if (existingReminderId) {
          await updateReminderFor(existingReminderId, remindAt);
        } else {
          await createReminderFor(updatedTask.id, remindAt);
        }
      } else if (!wantsReminder && existingReminderId) {
        await deleteReminderFor(existingReminderId);
      }

      closeEditModal();
      await loadTasks();
      showToast('Task updated successfully.');
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Error updating task:', error);
      alert(error.message || 'Unable to update task. Please try again.');
    } finally {
      if (updateTaskBtn) updateTaskBtn.disabled = false;
    }
  });

  // ============================================================
  // LOAD TASKS & RENDER
  // ============================================================

  async function loadTasks() {
    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items/me`);
      if (!response.ok) throw new Error(`Server responded ${response.status}`);

      allTasks = await response.json();
      await refreshConflicts();
      renderTasks();
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to load tasks:', error);
    }
  }

  function renderTasks() {
    if (!taskList) return;

    const visibleTasks = getVisibleTasks();

    if (taskCount) {
      taskCount.textContent = `${visibleTasks.length} task${visibleTasks.length === 1 ? '' : 's'}`;
    }

    if (visibleTasks.length === 0) {
      taskList.innerHTML = '';
      if (emptyState) emptyState.hidden = false;
      return;
    }

    if (emptyState) emptyState.hidden = true;

    taskList.innerHTML = visibleTasks.map(task => {
      const reminderBadgeHtml = renderReminderBadge(task);
      return `
        <div class="task-card ${priorityClass(task.priority)}" data-id="${task.id}">
          <div class="task-header">
            <h3 class="task-title">${escapeHtml(task.title)}</h3>
            <span class="task-priority">${escapeHtml(task.priority)}</span>
          </div>
          <p class="task-desc">${escapeHtml(task.description)}</p>
          <div class="task-meta">
            <span class="task-due">📅 ${formatDue(getTaskDate(task))}</span>
            ${reminderBadgeHtml}
          </div>
        </div>
      `;
    }).join('');
  }

  function priorityClass(priority) {
    return String(priority || 'MEDIUM').toLowerCase();
  }

  function getVisibleTasks() {
    return [...allTasks];
  }

  async function refreshConflicts() {
    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-conflicts/me`);
      if (response.ok) {
        applyConflictPairs(await response.json());
      }
    } catch (_) {}
  }

  function applyConflictPairs(pairs) {
    conflictPartnersByTaskId = new Map();
    (Array.isArray(pairs) ? pairs : []).forEach(pair => {
      if (pair?.itemOne && pair?.itemTwo) {
        const id1 = String(pair.itemOne.id);
        const id2 = String(pair.itemTwo.id);
        if (!conflictPartnersByTaskId.has(id1)) conflictPartnersByTaskId.set(id1, []);
        if (!conflictPartnersByTaskId.has(id2)) conflictPartnersByTaskId.set(id2, []);
        conflictPartnersByTaskId.get(id1).push(pair.itemTwo);
        conflictPartnersByTaskId.get(id2).push(pair.itemOne);
      }
    });
  }

  async function initPage() {
    // 1. Get profile element safely
    const profileEl = document.querySelector('.greeting-row') || document.querySelector('.profile-name');

    try {
      const response = await apiFetch(`${API_BASE}/api/auth/me`);
      if (response.ok) {
        const user = await response.json();
        if (profileEl) profileEl.textContent = user.fullName || user.email || 'Workspace';
      }
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed loading profile:', error);
      if (profileEl) profileEl.textContent = 'Workspace';
    }

    // 2. Load tasks safely
    try {
      await loadTasks();
    } catch (error) {
      console.error('Failed loading tasks:', error);
    }
  }

  // ============================================================
  // INITIALIZATION
  // ============================================================

  initPage();
  setupCreateTaskButton();
});