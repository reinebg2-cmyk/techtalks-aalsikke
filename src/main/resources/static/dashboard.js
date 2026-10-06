// Aal Sikke — shared dashboard script (loaded on dashboard.html, tasks.html and settings.html)
//
// Handles: auth, task create / edit / delete / status, reminders (create, edit,
// delete), conflicts, profile card, modals, bell, and exposes window.AalSikke
// for dashboard-board.js and tasks.js.
//
// Endpoints used:
// GET/POST        /api/schedule-items/me | /api/schedule-items
// PUT/DELETE      /api/schedule-items/{id}
// GET             /api/schedule-conflicts/me
// GET             /api/auth/me
// POST/PUT/DELETE /api/reminders[/{id}]

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token';

const DONE_STATUS = 'COMPLETED';
const PENDING_STATUS = 'PENDING';

document.addEventListener('DOMContentLoaded', () => {

  // ============================================================
  // AUTH
  // ============================================================

  if (!localStorage.getItem(TOKEN_KEY)) {
    window.location.href = 'login.html';
    return;
  }

  function redirectToLogin() {
    localStorage.removeItem(TOKEN_KEY);
    window.location.href = 'login.html';
  }

  async function apiFetch(url, options = {}) {
    const headers = {
      Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}`,
      ...(options.headers || {})
    };

    const response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
      redirectToLogin();
      const expired = new Error('Your session has expired.');
      expired.sessionExpired = true;
      throw expired;
    }
    return response;
  }

  // ============================================================
  // HELPERS
  // ============================================================

  const $ = id => document.getElementById(id);

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
      month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'
    });
  }

  function toDateTimeLocal(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    if (Number.isNaN(date.getTime())) return '';
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  function toISOStringFromLocal(value) {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }

  function getTaskDate(task) {
    return task.deadline || task.endTime || task.startTime;
  }

  function isDone(task) {
    return String(task.status || '').toUpperCase() === DONE_STATUS;
  }

  function firstReminder(task) {
    return Array.isArray(task.reminders) && task.reminders.length ? task.reminders[0] : null;
  }

  let toastTimer = null;
  function showToast(message) {
    const toast = $('toast');
    if (!toast) { alert(message); return; }
    toast.textContent = message;
    toast.classList.add('is-visible', 'show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('is-visible', 'show'), 4000);
  }

  async function readJsonSafely(response) {
    try { return await response.json(); } catch (_) { return null; }
  }

  async function errorMessage(response, fallback) {
    const data = await readJsonSafely(response);
    return (data && data.message) || `${fallback} (${response.status})`;
  }

  // ============================================================
  // STATE
  // ============================================================

  let allTasks = [];
  let conflictPartnersByTaskId = new Map();
  let tasksState = 'loading';
  let currentProfile = null;
  let originalRemindAtLocal = '';
  let editingTask = null;   // the task open in the edit modal (null when closed)

  // ============================================================
  // MODALS (profile / password / notification prefs / task edit)
  // ============================================================

  function openModal(modal) {
    if (modal) modal.hidden = false;
  }

  function closeModal(modal) {
    if (modal) modal.hidden = true;
  }

  const taskModal = $('task-modal');
  const profileModal = $('profile-modal');
  const passwordModal = $('password-modal');
  const notifPrefsModal = $('notif-prefs-modal');

  function closeAllModals() {
    [taskModal, profileModal, passwordModal, notifPrefsModal].forEach(closeModal);
    if (taskModal) {
      $('edit-task-form')?.reset();
      originalRemindAtLocal = '';
      editingTask = null;
    }
  }

  [taskModal, profileModal, passwordModal, notifPrefsModal].forEach(modal => {
    modal?.addEventListener('click', event => {
      if (event.target === modal) {
        closeModal(modal);
        if (modal === taskModal) {
          $('edit-task-form')?.reset();
          originalRemindAtLocal = '';
          editingTask = null;
        }
      }
    });
  });

  $('close-profile-modal')?.addEventListener('click', () => closeModal(profileModal));
  $('cancel-profile-btn')?.addEventListener('click', () => closeModal(profileModal));
  $('close-password-modal')?.addEventListener('click', () => closeModal(passwordModal));
  $('cancel-password-btn')?.addEventListener('click', () => closeModal(passwordModal));
  $('close-notif-prefs-modal')?.addEventListener('click', () => closeModal(notifPrefsModal));
  $('cancel-notif-prefs-btn')?.addEventListener('click', () => closeModal(notifPrefsModal));

  // ============================================================
  // TOP BAR MENUS
  // ============================================================

  const avatarBtn = $('avatar-btn');
  const profileDropdown = $('profile-dropdown');
  const notifBtn = $('notif-btn');
  const notifDropdown = $('notif-dropdown');

  function closeDropdowns() {
    if (profileDropdown) profileDropdown.hidden = true;
    if (notifDropdown) notifDropdown.hidden = true;
  }

  avatarBtn?.addEventListener('click', event => {
    event.stopPropagation();
    const open = profileDropdown.hidden;
    closeDropdowns();
    profileDropdown.hidden = !open;
  });

  notifBtn?.addEventListener('click', event => {
    event.stopPropagation();
    const open = notifDropdown.hidden;
    closeDropdowns();
    notifDropdown.hidden = !open;
  });

  document.addEventListener('click', event => {
    if (profileDropdown?.contains(event.target) || notifDropdown?.contains(event.target)) return;
    closeDropdowns();
  });

  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') { closeDropdowns(); closeAllModals(); }
  });

  function openProfileModal() {
    closeDropdowns();
    fillProfileModal();
    openModal(profileModal);
  }

  $('edit-profile-link')?.addEventListener('click', event => { event.preventDefault(); openProfileModal(); });
  $('sidebar-profile-btn')?.addEventListener('click', openProfileModal);

  $('change-password-link')?.addEventListener('click', event => {
    event.preventDefault();
    closeDropdowns();
    openModal(passwordModal);
  });

  $('notification-prefs-link')?.addEventListener('click', event => {
    event.preventDefault();
    closeDropdowns();
    openModal(notifPrefsModal);
  });

  $('logout-link')?.addEventListener('click', () => {
    localStorage.removeItem(TOKEN_KEY);
  });

  // ============================================================
  // PROFILE
  // ============================================================

  const ROLE_LABELS = { STUDENT: 'Student', WORK: 'Work', HYBRID: 'Dual' };

  function profileRole() {
    return String(currentProfile?.role || currentProfile?.mode || currentProfile?.userMode || '').toUpperCase();
  }

  function profileName() {
    return currentProfile?.fullName || currentProfile?.name || currentProfile?.username || '';
  }

  function initials(name) {
    const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '?';
    return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }

  async function loadProfile() {
    try {
      const response = await apiFetch(`${API_BASE}/api/auth/me`);
      if (!response.ok) return;
      currentProfile = await response.json();
      renderProfile();
    } catch (error) {
      if (!error.sessionExpired) console.error('Failed to load profile:', error);
    }
  }

  function renderProfile() {
    const name = profileName();
    const ini = initials(name);

    ['avatar-btn', 'profile-avatar', 'modal-profile-avatar'].forEach(id => {
      const el = $(id);
      if (el) el.textContent = ini;
    });

    if ($('profile-name')) $('profile-name').textContent = name || 'Your profile';

    const role = ROLE_LABELS[profileRole()];
    if ($('profile-role')) $('profile-role').textContent = role ? `${role} profile` : 'Profile';

    const greeting = $('greeting-text');
    if (greeting) {
      const hour = new Date().getHours();
      const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
      const first = name.trim().split(/\s+/)[0];
      greeting.textContent = first ? `${part}, ${first}` : part;
    }
  }

  function fillProfileModal() {
    if (!currentProfile) return;
    const set = (id, value) => { const el = $(id); if (el) el.value = value ?? ''; };

    set('profile-fullname', currentProfile.fullName || currentProfile.name);
    set('profile-username', currentProfile.username);
    set('profile-email', currentProfile.email);
    set('profile-phone', currentProfile.phoneNumber || currentProfile.phone);
    set('profile-dob', String(currentProfile.dateOfBirth || currentProfile.dob || '').slice(0, 10));
    set('profile-current-password', '');

    const role = profileRole();
    const radio = document.querySelector(`input[name="profile-role"][value="${role}"]`);
    if (radio) radio.checked = true;
  }

  // Saving profile / password / notification preferences is not part of this
  // script (their endpoints are not documented here) - tell the user plainly.
  [['profile-form', 'profile-message'],
   ['password-form', 'password-message'],
   ['notif-prefs-form', 'notif-prefs-message']].forEach(([formId, messageId]) => {
    $(formId)?.addEventListener('submit', event => {
      event.preventDefault();
      const message = $(messageId);
      if (message) message.textContent = 'Saving is not connected in this script yet.';
    });
  });

  // ============================================================
  // REMINDER API
  // ============================================================

  async function createReminderFor(scheduleItemId, remindAt) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scheduleItemId, remindAt })
      });
      if (!response.ok) showToast(await errorMessage(response, 'Task saved, but the reminder could not be set'));
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to create reminder:', error);
      showToast('Task saved, but the reminder could not be set.');
    }
  }

  async function updateReminderFor(reminderId, remindAt) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders/${reminderId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ remindAt })
      });
      if (!response.ok) showToast(await errorMessage(response, 'Task updated, but the reminder could not be updated'));
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to update reminder:', error);
      showToast('Task updated, but the reminder could not be updated.');
    }
  }

  async function deleteReminderFor(reminderId) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders/${reminderId}`, { method: 'DELETE' });
      if (!response.ok && response.status !== 204) console.error('Failed to delete reminder:', response.status);
    } catch (error) {
      if (!error.sessionExpired) console.error('Failed to delete reminder:', error);
    }
  }

  // ============================================================
  // START-TIME + REMINDER TOGGLES
  // ============================================================

  const WINDOW_TYPES = new Set(['CLASS', 'SHIFT', 'STUDY']);

  function setupStartToggle(checkbox, startField, categorySelect) {
    if (!checkbox || !startField) return null;

    let userChose = false;
    const sync = () => { startField.hidden = !checkbox.checked; };

    checkbox.addEventListener('change', () => { userChose = true; sync(); });

    categorySelect?.addEventListener('change', () => {
      if (!userChose) {
        checkbox.checked = WINDOW_TYPES.has(categorySelect.value);
        sync();
      }
    });

    return {
      presetFromCategory() {
        userChose = false;
        checkbox.checked = Boolean(categorySelect && WINDOW_TYPES.has(categorySelect.value));
        sync();
      },
      set(checked) { userChose = true; checkbox.checked = checked; sync(); },
      isChecked: () => checkbox.checked
    };
  }

  // Create form
  const taskForm = $('task-form');
  const taskTitleInput = $('task-title');
  const taskDescriptionInput = $('task-description');
  const taskDueInput = $('task-due') || $('task-deadline');
  const taskStartInput = $('task-start');
  const taskPriorityInput = $('task-priority');
  const taskCategoryInput = $('task-category');
  const saveTaskBtn = $('save-task-btn');

  const createStartToggle = setupStartToggle($('task-has-start'), $('field-task-start'), taskCategoryInput);
  createStartToggle?.presetFromCategory();

  const taskWantsReminder = $('task-wants-reminder');
  const taskReminderField = $('field-task-reminder');
  const taskReminderAt = $('task-reminder-at');

  function syncReminderField() {
    if (taskWantsReminder && taskReminderField) taskReminderField.hidden = !taskWantsReminder.checked;
  }
  taskWantsReminder?.addEventListener('change', syncReminderField);
  syncReminderField();

  // Edit form
  const editTaskForm = $('edit-task-form');
  const editTaskCategory = $('edit-task-category');
  const editStartToggle = setupStartToggle($('edit-task-has-start'), $('field-edit-task-start'), editTaskCategory);

  const editTaskWantsReminder = $('edit-task-wants-reminder');
  const editTaskReminderField = $('field-edit-task-reminder');
  const editTaskReminderAt = $('edit-task-reminder-at');
  const editReminderId = $('edit-reminder-id');

  function syncEditReminderField() {
    if (editTaskWantsReminder && editTaskReminderField) {
      editTaskReminderField.hidden = !editTaskWantsReminder.checked;
    }
  }
  editTaskWantsReminder?.addEventListener('change', syncEditReminderField);

  // ============================================================
  // CREATE TASK
  // ============================================================

  function openCreateForm() {
    if (!taskForm) return;
    taskForm.hidden = false;
    createStartToggle?.presetFromCategory();
    syncReminderField();
    taskForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
    taskTitleInput?.focus({ preventScroll: true });
  }

  function closeCreateForm() {
    if (!taskForm) return;
    taskForm.reset();
    taskForm.hidden = true;
    createStartToggle?.presetFromCategory();
    syncReminderField();
  }

  $('toggle-form-btn')?.addEventListener('click', () => {
    if (!taskForm) return;
    if (taskForm.hidden) openCreateForm(); else closeCreateForm();
  });
  $('cancel-task-btn')?.addEventListener('click', closeCreateForm);

  taskForm?.addEventListener('submit', async event => {
    event.preventDefault();

    const title = taskTitleInput?.value.trim();
    if (!title) { alert('Please enter a task title.'); return; }

    const deadline = toISOStringFromLocal(taskDueInput?.value);
    if (!deadline) { alert('Please select a due time or deadline.'); return; }

    let startTime = null;
    let endTime = null;

    if (createStartToggle?.isChecked()) {
      startTime = toISOStringFromLocal(taskStartInput?.value);
      if (!startTime) { alert('Please select a start time, or untick "Has a start time".'); return; }
      if (new Date(startTime) >= new Date(deadline)) { alert('Start time must be before the due time.'); return; }
      endTime = deadline;
    }

    let remindAt = null;
    if (taskWantsReminder?.checked) {
      remindAt = toISOStringFromLocal(taskReminderAt?.value);
      if (!remindAt) { alert('Please choose when to be reminded, or untick "Remind me".'); return; }
      if (new Date(remindAt) <= new Date()) { alert('The reminder must be in the future.'); return; }
      if (new Date(remindAt) > new Date(deadline)) { alert('The reminder cannot be after the due time.'); return; }
      if (startTime && new Date(remindAt) < new Date(startTime)) {
        alert('The reminder cannot be before the start time.');
        return;
      }
    }

    const payload = {
      title,
      description: taskDescriptionInput?.value.trim() || null,
      itemType: taskCategoryInput?.value || 'PERSONAL',
      priority: taskPriorityInput?.value || 'MEDIUM',
      status: PENDING_STATUS,
      startTime,
      endTime,
      deadline
    };

    if (saveTaskBtn) saveTaskBtn.disabled = true;

    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error(await errorMessage(response, 'Failed to create task'));

      const created = await response.json();
      if (remindAt) await createReminderFor(created.id, remindAt);

      closeCreateForm();
      await loadTasks();
      showToast('Task created.');
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Error creating task:', error);
      alert(error.message || 'Unable to create task. Please try again.');
    } finally {
      if (saveTaskBtn) saveTaskBtn.disabled = false;
    }
  });

  // ============================================================
  // EDIT TASK
  // ============================================================

  function openEditModal(task) {
    if (!taskModal || !task) return;
    editingTask = task;

    const set = (id, value) => { const el = $(id); if (el) el.value = value; };

    set('edit-task-id', task.id);
    set('edit-task-title', task.title || '');
    set('edit-task-description', task.description || '');
    set('edit-task-priority', task.priority || 'MEDIUM');
    set('edit-task-category', task.itemType || 'PERSONAL');
    set('edit-task-status', task.status || PENDING_STATUS);
    set('edit-task-deadline', toDateTimeLocal(task.deadline || task.endTime || ''));

    const hasStart = Boolean(task.startTime);
    editStartToggle?.set(hasStart);
    set('edit-task-start', hasStart ? toDateTimeLocal(task.startTime) : '');

    const reminder = firstReminder(task);
    if (editTaskWantsReminder) editTaskWantsReminder.checked = Boolean(reminder);

    originalRemindAtLocal = reminder && reminder.remindAt ? toDateTimeLocal(reminder.remindAt) : '';
    if (editTaskReminderAt) editTaskReminderAt.value = originalRemindAtLocal;
    if (editReminderId) editReminderId.value = reminder ? reminder.id : '';

    syncEditReminderField();
    openModal(taskModal);
  }

  function closeEditModal() {
    closeModal(taskModal);
    editTaskForm?.reset();
    if (editReminderId) editReminderId.value = '';
    originalRemindAtLocal = '';
    editingTask = null;
  }

  $('close-task-modal')?.addEventListener('click', closeEditModal);
  $('cancel-edit-btn')?.addEventListener('click', closeEditModal);

  editTaskForm?.addEventListener('submit', async event => {
    event.preventDefault();

    const id = $('edit-task-id')?.value;
    if (!id) return;

    const title = $('edit-task-title')?.value.trim();
    if (!title) { alert('Please enter a task title.'); return; }

    const deadline = toISOStringFromLocal($('edit-task-deadline')?.value);
    if (!deadline) { alert('Please select a due time or deadline.'); return; }

    let startTime = null;
    let endTime = null;
    // A start-only task (start, no end) stays one: this form has no end-time field
    const keepStartOnly = Boolean(editingTask && editingTask.startTime && !editingTask.endTime);

    if (editStartToggle?.isChecked()) {
      startTime = toISOStringFromLocal($('edit-task-start')?.value);
      if (!startTime) { alert('Please select a start time, or untick "Has a start time".'); return; }
      if (!keepStartOnly) {
        if (new Date(startTime) >= new Date(deadline)) { alert('Start time must be before the due time.'); return; }
        endTime = deadline;
      }
    }

    const wantsReminder = Boolean(editTaskWantsReminder?.checked);
    const existingReminderId = editReminderId?.value || null;
    let remindAt = null;
    let reminderChanged = false;

    if (wantsReminder) {
      const remindAtLocal = editTaskReminderAt?.value || '';
      remindAt = toISOStringFromLocal(remindAtLocal);
      if (!remindAt) { alert('Please choose when to be reminded, or untick "Remind me".'); return; }

      reminderChanged = !existingReminderId || remindAtLocal !== originalRemindAtLocal;

      // Only insist on a future time when the reminder is new or was changed
      if (reminderChanged && new Date(remindAt) <= new Date()) {
        alert('The reminder must be in the future.');
        return;
      }
      if (new Date(remindAt) > new Date(deadline)) {
        alert('The reminder cannot be after the due time.');
        return;
      }
      if (startTime && new Date(remindAt) < new Date(startTime)) {
        alert('The reminder cannot be before the start time.');
        return;
      }
    }

    const payload = {
      title,
      description: $('edit-task-description')?.value.trim() || null,
      itemType: editTaskCategory?.value || 'PERSONAL',
      priority: $('edit-task-priority')?.value || 'MEDIUM',
      status: $('edit-task-status')?.value || PENDING_STATUS,
      startTime,
      endTime,
      deadline,
      // keep a start-only task as a marker (the backend clears any old end time)
      ...(keepStartOnly && startTime ? { timeType: 'START_ONLY' } : {}),
      // unticking "Has a start time" on a scheduled task turns it back into a deadline-only task
      clearTimeSlot: !editStartToggle?.isChecked() && Boolean(editingTask?.startTime)
    };

    const updateBtn = $('update-task-btn');
    if (updateBtn) updateBtn.disabled = true;

    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error(await errorMessage(response, 'Failed to update task'));

      const updated = await readJsonSafely(response);

      if (wantsReminder && remindAt) {
        if (existingReminderId) {
          if (reminderChanged) await updateReminderFor(existingReminderId, remindAt);
        } else {
          await createReminderFor((updated && updated.id) || id, remindAt);
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
      if (updateBtn) updateBtn.disabled = false;
    }
  });

  // ============================================================
  // STATUS + DELETE
  // ============================================================

  // Returns true on success. Uses the same PUT as the edit form.
  async function setTaskStatus(task, status) {
    const payload = {
      title: task.title,
      description: task.description || null,
      itemType: task.itemType || 'PERSONAL',
      priority: task.priority || 'MEDIUM',
      status,
      startTime: task.startTime || null,
      endTime: task.endTime || null,
      deadline: task.deadline || task.endTime || null
    };

    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items/${task.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error(await errorMessage(response, 'Failed to update task'));

      await loadTasks();
      showToast(status === DONE_STATUS ? 'Task completed 🎉' : 'Task reopened.');
      return true;
    } catch (error) {
      if (!error.sessionExpired) {
        console.error('Error changing task status:', error);
        showToast(error.message || 'Could not update the task.');
      }
      return false;
    }
  }

  async function deleteTask(id) {
    const task = allTasks.find(t => String(t.id) === String(id));
    if (!confirm(`Delete "${task ? task.title : 'this task'}"?`)) return false;

    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items/${id}`, { method: 'DELETE' });
      if (!response.ok && response.status !== 204) {
        throw new Error(await errorMessage(response, 'Failed to delete task'));
      }
      await loadTasks();
      showToast('Task deleted.');
      return true;
    } catch (error) {
      if (!error.sessionExpired) {
        console.error('Error deleting task:', error);
        showToast(error.message || 'Could not delete the task.');
      }
      return false;
    }
  }

  // ============================================================
  // LOAD TASKS + CONFLICTS
  // ============================================================

  async function refreshConflicts() {
    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-conflicts/me`);
      if (response.ok) applyConflictPairs(await response.json());
    } catch (error) {
      if (error.sessionExpired) throw error;
    }
  }

  function applyConflictPairs(pairs) {
    conflictPartnersByTaskId = new Map();
    (Array.isArray(pairs) ? pairs : []).forEach(pair => {
      if (!pair?.itemOne || !pair?.itemTwo) return;
      const id1 = String(pair.itemOne.id);
      const id2 = String(pair.itemTwo.id);
      if (!conflictPartnersByTaskId.has(id1)) conflictPartnersByTaskId.set(id1, []);
      if (!conflictPartnersByTaskId.has(id2)) conflictPartnersByTaskId.set(id2, []);
      conflictPartnersByTaskId.get(id1).push(pair.itemTwo);
      conflictPartnersByTaskId.get(id2).push(pair.itemOne);
    });
  }

  async function loadTasks() {
    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-items/me`);
      if (!response.ok) throw new Error(`Server responded ${response.status}`);

      allTasks = await response.json();
      await refreshConflicts();
      tasksState = 'ready';

      renderSummary();
      renderNotifications();
      document.dispatchEvent(new CustomEvent('aalsikke:tasks-loaded'));
    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to load tasks:', error);
      tasksState = 'error';
      document.dispatchEvent(new CustomEvent('aalsikke:tasks-error'));
      showToast('Could not load your tasks.');
    }
  }

  // ============================================================
  // SUMMARY (greeting line, dashboard cards, sidebar meta)
  // ============================================================

  function renderSummary() {
    const now = new Date();
    let overdue = 0, today = 0, upcoming = 0, done = 0;

    allTasks.forEach(task => {
      if (isDone(task)) { done++; return; }
      const value = getTaskDate(task);
      if (!value) return;
      const date = new Date(value);
      if (date < now) overdue++;
      else if (date.toDateString() === now.toDateString()) today++;
      else upcoming++;
    });

    const open = allTasks.length - done;

    const dateEl = $('current-date');
    if (dateEl) {
      dateEl.textContent = now.toLocaleDateString(undefined, {
        weekday: 'long', month: 'long', day: 'numeric'
      });
    }

    const summary = $('greeting-summary');
    if (summary) {
      summary.textContent = allTasks.length === 0
        ? 'You have no tasks yet.'
        : `${open} open task${open === 1 ? '' : 's'}` +
          (overdue ? `, ${overdue} overdue` : '') +
          (today ? `, ${today} due today` : '') + '.';
    }

    const meta = $('profile-meta');
    if (meta) meta.textContent = `${open} open · ${done} completed`;

    const cards = $('dashboard-cards');
    if (cards) {
      const items = [
        ['Overdue', overdue],
        ['Due today', today],
        ['Upcoming', upcoming],
        ['Completed', done]
      ];
      cards.innerHTML = items.map(([label, count]) => `
        <div class="card dashboard-card">
          <p class="dashboard-card-label">${label}</p>
          <p class="dashboard-card-count">${count}</p>
        </div>`).join('');
    }
  }

  // ============================================================
  // BELL (reminders due within 24h, or already passed, on open tasks)
  // ============================================================

  function renderNotifications() {
    const list = $('notif-list');
    const empty = $('notif-empty');
    const badge = $('notif-badge');
    if (!list) return;

    const limit = Date.now() + 24 * 60 * 60 * 1000;

    const due = allTasks
      .filter(task => !isDone(task))
      .map(task => ({ task, reminder: firstReminder(task) }))
      .filter(({ reminder }) => reminder && reminder.remindAt && new Date(reminder.remindAt).getTime() <= limit)
      .sort((a, b) => new Date(a.reminder.remindAt) - new Date(b.reminder.remindAt));

    list.innerHTML = due.map(({ task, reminder }) => `
      <li class="notif-item">
        <strong>${escapeHtml(task.title)}</strong>
        <span>${escapeHtml(formatDue(reminder.remindAt))}</span>
      </li>`).join('');

    if (empty) empty.hidden = due.length > 0;
    if (badge) badge.hidden = due.length === 0;
  }

  // ============================================================
  // PUBLIC API for dashboard-board.js and tasks.js
  // ============================================================

  window.AalSikke = {
    API_BASE,
    apiFetch,
    getTasks: () => allTasks,
    getConflicts: () => conflictPartnersByTaskId,
    getTasksState: () => tasksState,
    reloadTasks: loadTasks,
    openEditModal,
    setTaskStatus,
    deleteTask,
    openCreateForm
  };

  // ============================================================
  // START
  // ============================================================

  renderSummary();
  loadProfile();
  loadTasks();

  // "+" button on the dashboard links to tasks.html#new-task
  if (taskForm && location.hash === '#new-task') openCreateForm();
});