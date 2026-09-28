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



// Same origin as the page (like login.js and register.js). The dashboard is
// served by Spring Boot, so this is also where the API lives.
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

  // Read the token on every request: when the user changes their email the
  // backend issues a new JWT and it is stored under the same key.
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

  // Role / status filter dropdowns, sort dropdown and result count
  const roleFilterSelect = document.getElementById('role-filter');
  const statusFilterSelect = document.getElementById('status-filter');
  const sortSelect = document.getElementById('sort-select');
  const taskCount = document.getElementById('task-count');

  const avatarBtn = document.getElementById('avatar-btn');
  const profileDropdown = document.getElementById('profile-dropdown');
  const notifBtn = document.getElementById('notif-btn');
  const notifDropdown = document.getElementById('notif-dropdown');
  const notifList = document.getElementById('notif-list');
  const notifEmpty = document.getElementById('notif-empty');
  const notifBadge = document.getElementById('notif-badge');
  const editProfileLink = document.getElementById('edit-profile-link');
  const sidebarProfileBtn =
      document.getElementById('sidebar-profile-btn');

  // Profile modal
  const profileModal = document.getElementById('profile-modal');
  const profileForm = document.getElementById('profile-form');
  const closeProfileBtn =
      document.getElementById('close-profile-modal');
  const cancelProfileBtn =
      document.getElementById('cancel-profile-btn');
  const saveProfileBtn =
      document.getElementById('save-profile-btn');

  const profileFullname =
      document.getElementById('profile-fullname');
  const profileUsername =
      document.getElementById('profile-username');
  const profileEmail = document.getElementById('profile-email');
  const profileDob = document.getElementById('profile-dob');
  const profilePhone = document.getElementById('profile-phone');
  const profileCurrentPassword =
      document.getElementById('profile-current-password'); // KAN-257
  const profileRoleRadios =
      document.querySelectorAll('input[name="profile-role"]');
  const profileRoleHint =
      document.getElementById('profile-role-hint');
  const profileMessage =
      document.getElementById('profile-message');

  const modalProfileAvatar =
      document.getElementById('modal-profile-avatar');

  // Edit task modal
  const taskModal = document.getElementById('task-modal');
  const editTaskForm =
      document.getElementById('edit-task-form');

  const editTaskId =
      document.getElementById('edit-task-id');
  const editTaskTitle =
      document.getElementById('edit-task-title');
  const editTaskDescription =
      document.getElementById('edit-task-description');
  const editTaskDeadline =
      document.getElementById('edit-task-deadline');
  const editTaskPriority =
      document.getElementById('edit-task-priority');
  const editTaskCategory =
      document.getElementById('edit-task-category');
  const editTaskStatus =
      document.getElementById('edit-task-status');

  const closeTaskBtn =
      document.getElementById('close-task-modal');
  const cancelEditBtn =
      document.getElementById('cancel-edit-btn');
  const updateTaskBtn =
      document.getElementById('update-task-btn');

  // ============================================================
  // STATE
  // ============================================================

  let allTasks = [];

  // Populated by refreshConflicts() from GET /api/schedule-conflicts/me (S3-07).
  // conflictPartnersByTaskId maps a task's id (string) -> the other items it
  // clashes with, so renderTasks() can mark cards without re-fetching.
  let conflictPartnersByTaskId = new Map();

  let activeFilter = 'all';
  let activeRoleFilter = 'ALL';      // ALL | STUDENT | WORK
  let activeStatusFilter = 'ALL';    // ALL | PENDING | COMPLETED | OVERDUE
  let activeSort = 'DEFAULT';        // DEFAULT | DEADLINE | PRIORITY
  let currentProfile = null;
  let dueReminders = [];

  // Loading state of the two things the summary cards depend on.
  // 'loading' -> skeletons, 'ready' -> real numbers, 'error' -> message.
  let tasksState = 'loading';
  let profileState = 'loading';

  const studentTypes = new Set([
    'ASSIGNMENT',
    'EXAM',
    'CLASS',
    'STUDY'
  ]);

  const workTypes = new Set([
    'SHIFT'
  ]);

  // ============================================================
  // BASIC HELPERS
  // ============================================================

  function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value ?? '';
    return div.innerHTML;
  }

  function getInitials(name) {
    if (!name) {
      return '??';
    }

    return name
        .trim()
        .split(/\s+/)
        .map(part => part.charAt(0))
        .join('')
        .toUpperCase()
        .slice(0, 2);
  }

  function formatProfileMode(mode) {
    if (!mode) {
      return '';
    }

    if (mode === 'HYBRID') {
      return 'Dual';
    }

    return mode.charAt(0) +
        mode.slice(1).toLowerCase();
  }

  function formatDue(dateStr) {
    if (!dateStr) {
      return 'No deadline';
    }

    const date = new Date(dateStr);

    if (Number.isNaN(date.getTime())) {
      return 'Invalid date';
    }

    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit'
    });
  }

  function toDateTimeLocal(dateStr) {
    if (!dateStr) {
      return '';
    }

    const date = new Date(dateStr);

    if (Number.isNaN(date.getTime())) {
      return '';
    }

    const offset = date.getTimezoneOffset();
    const localDate =
        new Date(date.getTime() - offset * 60000);

    return localDate.toISOString().slice(0, 16);
  }

  function toISOStringFromLocal(value) {
    if (!value) {
      return null;
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return null;
    }

    return date.toISOString();
  }

  function getTaskDate(task) {
    return task.deadline || task.startTime;
  }

  // ============================================================
  // SCHEDULE CONFLICTS (S3-07 / KAN-233)
  // GET /api/schedule-conflicts/me
  // ============================================================

  function applyConflictPairs(pairs) {
    conflictPartnersByTaskId = new Map();

    (Array.isArray(pairs) ? pairs : []).forEach(pair => {
      const first = pair && pair.itemOne;
      const second = pair && pair.itemTwo;

      if (!first || !second) {
        return;
      }

      const firstId = String(first.id);
      const secondId = String(second.id);

      if (!conflictPartnersByTaskId.has(firstId)) {
        conflictPartnersByTaskId.set(firstId, []);
      }
      if (!conflictPartnersByTaskId.has(secondId)) {
        conflictPartnersByTaskId.set(secondId, []);
      }

      conflictPartnersByTaskId.get(firstId).push(second);
      conflictPartnersByTaskId.get(secondId).push(first);
    });
  }

  // Re-fetches every conflicting pair among the user's own items and rebuilds
  // conflictPartnersByTaskId. Called after the initial task load and again
  // whenever a task is created or edited, since a saved item's new time can
  // both create and clear clashes for other items too.
  async function refreshConflicts() {
    try {
      const response = await apiFetch(`${API_BASE}/api/schedule-conflicts/me`);

      if (!response.ok) {
        throw new Error(`Server responded ${response.status}`);
      }

      applyConflictPairs(await response.json());

    } catch (error) {

      if (error.sessionExpired) {
        return;
      }

      // Non-fatal: the task list still renders, just without conflict badges.
      console.error('Failed to load schedule conflicts:', error);
    }
  }

  function describeConflictPartners(partners) {
    const names = partners.map(
        partner => `${partner.title} (${formatDue(partner.startTime)})`
    );

    if (names.length <= 2) {
      return names.join(' and ');
    }

    return `${names.slice(0, 2).join(', ')}, and ${names.length - 2} more`;
  }

  // Shows a toast naming the clashing item(s) when the just-saved task has
  // any conflicts (KAN-233). Silent when there are none, so saving a task
  // that clashes with nothing doesn't get a redundant toast.
  function announceConflictsIfAny(taskId) {
    const partners = conflictPartnersByTaskId.get(String(taskId)) || [];

    if (partners.length > 0) {
      showToast(`Saved — this clashes with ${describeConflictPartners(partners)}.`);
    }
  }

  function isToday(dateStr) {
    if (!dateStr) {
      return false;
    }

    const date = new Date(dateStr);

    if (Number.isNaN(date.getTime())) {
      return false;
    }

    const now = new Date();

    return date.toDateString() === now.toDateString();
  }

  function isUpcoming(dateStr) {
    if (!dateStr) {
      return false;
    }

    const date = new Date(dateStr);

    if (Number.isNaN(date.getTime())) {
      return false;
    }

    return date > new Date() &&
        !isToday(dateStr);
  }

  function isOverdue(task) {
    if (!task || String(task.status || '').toUpperCase() === 'COMPLETED') {
      return false;
    }

    const dateStr = getTaskDate(task);

    if (!dateStr) {
      return false;
    }

    return new Date(dateStr) < new Date();
  }

  function priorityClass(priority) {
    return String(priority || 'MEDIUM').toLowerCase();
  }

  function roleForTask(task) {
    const type =
        String(task.itemType || '').toUpperCase();

    if (studentTypes.has(type)) {
      return 'STUDENT';
    }

    if (workTypes.has(type)) {
      return 'WORK';
    }

    return 'PERSONAL';
  }

  // ============================================================
  // FILTERING + SORTING (all local — no API calls)
  // ============================================================

  const ROLE_FILTERS = new Set(['ALL', 'STUDENT', 'WORK']);
  const STATUS_FILTERS = new Set(['ALL', 'PENDING', 'COMPLETED', 'OVERDUE']);
  const SORT_MODES = new Set(['DEFAULT', 'DEADLINE', 'PRIORITY']);

  // Higher number = more important. A missing/unknown priority is treated as
  // MEDIUM, which is the backend's default (ScheduleItem.priority).
  const PRIORITY_RANK = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };

  /*
   * The backend only stores PENDING and COMPLETED. "Overdue" is derived:
   * a task that is not completed and whose deadline has passed. Every task
   * therefore falls into exactly one of the three buckets, so
   * Pending + Completed + Overdue always adds up to the total.
   */
  function taskStatus(task) {
    if (String(task.status || '').toUpperCase() === 'COMPLETED') {
      return 'COMPLETED';
    }

    return isOverdue(task) ? 'OVERDUE' : 'PENDING';
  }

  function matchesRole(task, role) {
    return role === 'ALL' || roleForTask(task) === role;
  }

  function matchesStatus(task, status) {
    return status === 'ALL' || taskStatus(task) === status;
  }

  function priorityRank(task) {
    return PRIORITY_RANK[String(task.priority || '').toUpperCase()]
        || PRIORITY_RANK.MEDIUM;
  }

  // Milliseconds since epoch, or Infinity when there is no usable date so that
  // undated tasks sort last.
  function deadlineTime(task) {
    const time = new Date(getTaskDate(task) || '').getTime();
    return Number.isNaN(time) ? Infinity : time;
  }

  // Plain comparison (a - b would give NaN for Infinity - Infinity).
  function compareNumbers(a, b) {
    if (a === b) {
      return 0;
    }

    return a < b ? -1 : 1;
  }

  function compareByDeadline(a, b) {
    return compareNumbers(deadlineTime(a), deadlineTime(b));
  }

  function compareByPriority(a, b) {
    // Highest priority first; ties fall back to the soonest deadline.
    return compareNumbers(priorityRank(b), priorityRank(a))
        || compareByDeadline(a, b);
  }

  // The dashboard's original ordering: overdue first, then by date.
  function compareByDefault(a, b) {
    const aGroup = isOverdue(a) ? 0 : 1;
    const bGroup = isOverdue(b) ? 0 : 1;

    return compareNumbers(aGroup, bGroup) || compareByDeadline(a, b);
  }

  function sortComparator(mode) {
    if (mode === 'DEADLINE') {
      return compareByDeadline;
    }

    if (mode === 'PRIORITY') {
      return compareByPriority;
    }

    return compareByDefault;
  }

  /*
   * Builds the list that is shown: tab filter -> role filter -> status filter
   * -> sort. Works on a copy, so allTasks (the data loaded from the server)
   * is never reordered or trimmed. Array.prototype.sort is stable, so tasks
   * that compare equal keep the order the server returned them in.
   */
  function getVisibleTasks() {
    let tasks = [...allTasks];

    if (activeFilter === 'today') {
      tasks = tasks.filter(task => isToday(getTaskDate(task)));
    } else if (activeFilter === 'upcoming') {
      tasks = tasks.filter(task => isUpcoming(getTaskDate(task)));
    }

    tasks = tasks.filter(task =>
        matchesRole(task, activeRoleFilter) &&
        matchesStatus(task, activeStatusFilter)
    );

    return tasks.sort(sortComparator(activeSort));
  }

  // ============================================================
  // CURRENT DATE
  // ============================================================

  function updateCurrentDate() {
    const currentDate =
        document.getElementById('current-date');

    if (!currentDate) {
      return;
    }

    currentDate.textContent =
        new Date().toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        });
  }

  // ============================================================
  // PROFILE DROPDOWN
  // ============================================================

  if (avatarBtn && profileDropdown) {
    avatarBtn.addEventListener('click', (event) => {
      event.stopPropagation();

      profileDropdown.hidden =
          !profileDropdown.hidden;
    });
  }

  document.addEventListener('click', (event) => {
    if (
        profileDropdown &&
        !event.target.closest('.profile-menu')
    ) {
      profileDropdown.hidden = true;
    }
  });

  document.addEventListener('click', event => {
    if (!event.target.closest('.task-actions')) {
      document.querySelectorAll('.task-menu').forEach(menu => {
        menu.hidden = true;
      });
      document.querySelectorAll('.task-menu-btn').forEach(button => {
        button.setAttribute('aria-expanded', 'false');
      });
    }
  });

  // ============================================================
  // QUICK CAPTURE FORM
  // ============================================================

  if (toggleFormBtn && taskForm) {
    toggleFormBtn.addEventListener('click', () => {
      taskForm.hidden = !taskForm.hidden;

      if (!taskForm.hidden) {
        const titleInput =
            document.getElementById('task-title');

        if (titleInput) {
          titleInput.focus();
        }
      }
    });
  }

  if (cancelTaskBtn && taskForm) {
    cancelTaskBtn.addEventListener('click', () => {
      taskForm.reset();
      taskForm.hidden = true;
    });
  }

  // ============================================================
  // FILTER TABS
  // ============================================================

  filterTabs.forEach(tab => {
    tab.addEventListener('click', () => {

      filterTabs.forEach(item => {
        item.classList.remove('active');
      });

      tab.classList.add('active');

      activeFilter =
          tab.dataset.filter || 'all';

      renderTasks();
    });
  });

  // ============================================================
  // ROLE / STATUS FILTERS + SORT DROPDOWNS
  // ============================================================

  // Ties a <select> to a piece of state. An unexpected value falls back to the
  // default option instead of silently hiding every task.
  function bindDropdown(select, allowedValues, fallback, onChange) {
    if (!select) {
      return;
    }

    select.addEventListener('change', () => {
      const value = allowedValues.has(select.value) ? select.value : fallback;
      select.classList.toggle('is-active', value !== fallback);
      onChange(value);
      renderTasks();
    });
  }

  bindDropdown(roleFilterSelect, ROLE_FILTERS, 'ALL', value => {
    activeRoleFilter = value;
  });

  bindDropdown(statusFilterSelect, STATUS_FILTERS, 'ALL', value => {
    activeStatusFilter = value;
  });

  bindDropdown(sortSelect, SORT_MODES, 'DEFAULT', value => {
    activeSort = value;
  });

  // ============================================================
  // CATEGORY OPTIONS
  // ============================================================

  function updateCategoryOptions(profileModeValue) {

    const categorySelect =
        document.getElementById('task-category');

    if (!categorySelect) {
      return;
    }

    const studentOptions = [
      'ASSIGNMENT',
      'EXAM',
      'CLASS',
      'STUDY'
    ];

    const workOptions = [
      'SHIFT'
    ];

    const sharedOptions = [
      'PERSONAL'
    ];

    let visibleValues;

    if (profileModeValue === 'STUDENT') {

      visibleValues = [
        ...studentOptions,
        ...sharedOptions
      ];

    } else if (profileModeValue === 'WORK') {

      visibleValues = [
        ...workOptions,
        ...sharedOptions
      ];

    } else {

      // HYBRID
      visibleValues = [
        ...studentOptions,
        ...workOptions,
        ...sharedOptions
      ];
    }

    Array.from(categorySelect.options)
        .forEach(option => {

          option.hidden =
              !visibleValues.includes(option.value);
        });

    const selected =
        categorySelect.selectedOptions[0];

    if (selected && selected.hidden) {

      const firstVisible =
          Array.from(categorySelect.options)
              .find(option => !option.hidden);

      if (firstVisible) {
        categorySelect.value =
            firstVisible.value;
      }
    }
  }

  // ============================================================
  // PROFILE — CONSTANTS + HELPERS
  // ============================================================

  // "Dual" is the label users see; the API and database store it as HYBRID.
  const VALID_PROFILE_MODES = ['STUDENT', 'WORK', 'HYBRID'];

  const ROLE_DESCRIPTIONS = {
    STUDENT: 'Assignments, exams, classes and study sessions.',
    WORK: 'Shifts and work deadlines.',
    HYBRID: 'Student and work items together in one dashboard.'
  };

  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  // Field name used by the API (ApiError.field) -> wrapper element id.
  const PROFILE_FIELD_IDS = {
    fullName: 'field-profile-fullname',
    email: 'field-profile-email',
    phoneNumber: 'field-profile-phone',
    profileMode: 'field-profile-mode'
  };

  // Order used when deciding which invalid field to focus first.
  const PROFILE_FIELD_ORDER = ['fullName', 'email', 'phoneNumber', 'profileMode'];

  let lastFocusedBeforeProfileModal = null;
  let profileCloseTimer = null;
  let toastTimer = null;

  function showToast(message) {
    const toast = document.getElementById('toast');

    if (!toast) {
      return;
    }

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
      return null; // empty or non-JSON body
    }
  }

  // ============================================================
  // PROFILE — RENDER + LOAD
  // ============================================================

  function renderProfile(profile) {

    const fullName =
        profile.fullName ||
        profile.username ||
        'User';

    const initials = getInitials(fullName);

    const profileAvatar = document.getElementById('profile-avatar');
    const profileName = document.getElementById('profile-name');
    const profileRole = document.getElementById('profile-role');
    const greetingText = document.getElementById('greeting-text');

    if (profileAvatar) {
      profileAvatar.textContent = initials;
    }

    if (profileName) {
      profileName.textContent = fullName;
    }

    if (profileRole) {
      profileRole.textContent = formatProfileMode(profile.profileMode);
    }

    if (greetingText) {
      greetingText.textContent = `Welcome back, ${fullName.split(' ')[0]}`;
    }

    if (avatarBtn) {
      avatarBtn.textContent = initials;
    }

    // Quick-capture categories follow the selected role.
    updateCategoryOptions(profile.profileMode);

    // The profile mode decides which role cards are shown.
    renderSummaryCards();
  }

  async function loadProfile() {

    try {

      const response = await apiFetch(`${API_BASE}/api/auth/me`);

      if (!response.ok) {
        throw new Error(`Server responded ${response.status}`);
      }

      currentProfile = await response.json();
      profileState = 'ready';
      renderProfile(currentProfile);

    } catch (error) {

      if (error.sessionExpired) {
        return;
      }

      console.error('Failed to load profile:', error);

      // Without a profile mode the role cards fall back to showing both.
      profileState = 'error';
      renderSummaryCards();
    }
  }

  // ============================================================
  // PROFILE MODAL — FORM STATE
  // ============================================================

  function getSelectedRole() {
    const checked = Array.from(profileRoleRadios).find(radio => radio.checked);
    return checked ? checked.value : '';
  }

  function setSelectedRole(mode) {
    profileRoleRadios.forEach(radio => {
      radio.checked = radio.value === mode;
    });
    updateRoleHint();
  }

  function updateRoleHint() {
    if (profileRoleHint) {
      profileRoleHint.textContent = ROLE_DESCRIPTIONS[getSelectedRole()] || '';
    }
  }

  function readProfileForm() {
    return {
      fullName: profileFullname ? profileFullname.value.trim() : '',
      email: profileEmail ? profileEmail.value.trim() : '',
      profileMode: getSelectedRole(),
      phoneNumber: profilePhone ? profilePhone.value.trim() : '',
      // KAN-257: backend requires it only when email or phone changes
      currentPassword: profileCurrentPassword ? profileCurrentPassword.value : ''
    };
  }

  function hasProfileChanges(values) {
    return (
        values.fullName !== (currentProfile.fullName || '') ||
        values.email !== (currentProfile.email || '') ||
        values.profileMode !== (currentProfile.profileMode || '') ||
        values.phoneNumber !== (currentProfile.phoneNumber || '')
    );
  }

  function populateProfileForm() {

    if (!currentProfile) {
      return;
    }

    if (profileFullname) {
      profileFullname.value = currentProfile.fullName || '';
    }

    if (profileUsername) {
      profileUsername.value = currentProfile.username || '';
    }

    if (profileEmail) {
      profileEmail.value = currentProfile.email || '';
    }

    if (profileDob) {
      profileDob.value = currentProfile.dateOfBirth || '';
    }

    if (profilePhone) {
      profilePhone.value = currentProfile.phoneNumber || '';
    }
    // KAN-257: never keep a password in the form between opens/saves
    if (profileCurrentPassword) {
      profileCurrentPassword.value = '';
    }


    if (modalProfileAvatar) {
      modalProfileAvatar.textContent = getInitials(
          currentProfile.fullName || currentProfile.username
      );
    }

    setSelectedRole(currentProfile.profileMode || '');

    clearProfileMessage();
    clearFieldErrors();
  }

  // ============================================================
  // PROFILE MODAL — MESSAGES + FIELD ERRORS
  // ============================================================

  function clearProfileMessage() {

    if (!profileMessage) {
      return;
    }

    profileMessage.textContent = '';
    profileMessage.className = 'modal-message';
  }

  // type: 'success' | 'error' | 'info'
  function showProfileMessage(message, type = 'info') {

    if (!profileMessage) {
      return;
    }

    profileMessage.textContent = message;
    profileMessage.className = `modal-message is-${type}`;
  }

  function getFieldInput(name) {
    const wrapper = document.getElementById(PROFILE_FIELD_IDS[name]);
    return wrapper ? wrapper.querySelector('input') : null;
  }

  function setFieldError(name, message) {

    const wrapper = document.getElementById(PROFILE_FIELD_IDS[name]);

    if (!wrapper) {
      return;
    }

    wrapper.classList.add('is-error');

    const hint = wrapper.querySelector('.hint.error-text');

    if (hint) {
      hint.textContent = message;
    }

    const input = getFieldInput(name);

    if (input) {
      input.setAttribute('aria-invalid', 'true');
    }
  }

  function clearFieldError(name) {

    const wrapper = document.getElementById(PROFILE_FIELD_IDS[name]);

    if (!wrapper) {
      return;
    }

    wrapper.classList.remove('is-error');

    const hint = wrapper.querySelector('.hint.error-text');

    if (hint) {
      hint.textContent = '';
    }

    const input = getFieldInput(name);

    if (input) {
      input.removeAttribute('aria-invalid');
    }
  }

  function clearFieldErrors() {
    Object.keys(PROFILE_FIELD_IDS).forEach(clearFieldError);
  }

  // Shows every error and moves focus to the first invalid field.
  function showFieldErrors(errors) {

    Object.entries(errors).forEach(([name, message]) => {
      setFieldError(name, message);
    });

    const firstInvalid = PROFILE_FIELD_ORDER.find(name => errors[name]);

    if (!firstInvalid) {
      return;
    }

    if (firstInvalid === 'profileMode') {
      const firstRadio = profileRoleRadios[0];
      const target = Array.from(profileRoleRadios).find(radio => radio.checked) || firstRadio;
      if (target) {
        target.focus();
      }
      return;
    }

    const input = getFieldInput(firstInvalid);

    if (input) {
      input.focus();
    }
  }

  // ============================================================
  // PROFILE MODAL — VALIDATION (client side)
  // ============================================================

  function isPlausiblePhone(value) {
    const digits = value.replace(/\D/g, '');

    return (
        /^[+\d][\d\s().-]*$/.test(value) &&
        digits.length >= 7 &&
        digits.length <= 15
    );
  }

  function validateProfileValues(values) {

    const errors = {};

    if (!values.fullName) {
      errors.fullName = 'Full name is required.';
    } else if (values.fullName.length > 255) {
      errors.fullName = 'Full name must be 255 characters or fewer.';
    }

    if (!values.email) {
      errors.email = 'Email is required.';
    } else if (!EMAIL_PATTERN.test(values.email)) {
      errors.email = 'Please enter a valid email address.';
    } else if (values.email.length > 255) {
      errors.email = 'Email must be 255 characters or fewer.';
    }

    if (!VALID_PROFILE_MODES.includes(values.profileMode)) {
      errors.profileMode = 'Please choose Student, Work or Dual.';
    }

    // Phone is optional. Registration accepts free-form numbers, so only
    // check the format when the user actually changed it — that way an
    // older, oddly formatted number never blocks saving other changes.
    const phoneChanged = values.phoneNumber !== (currentProfile.phoneNumber || '');

    if (values.phoneNumber && phoneChanged && !isPlausiblePhone(values.phoneNumber)) {
      errors.phoneNumber = 'Please enter a valid phone number (7–15 digits).';
    }

    return errors;
  }

  // ============================================================
  // PROFILE MODAL — BACKEND ERRORS
  // ============================================================

  // The backend answers with either
  //   409 -> { field, message }            (duplicate email / phone)
  //   400 -> [ { field, message }, ... ]   (bean validation)
  //   4xx -> { message } or { error }      (everything else)
  function parseApiErrors(body) {

    const fieldErrors = {};
    let general = '';

    const items = Array.isArray(body) ? body : (body ? [body] : []);

    items.forEach(item => {

      if (!item || typeof item !== 'object') {
        return;
      }

      const message = item.message || item.error;

      if (!message) {
        return;
      }

      if (item.field && PROFILE_FIELD_IDS[item.field]) {
        if (!fieldErrors[item.field]) {
          fieldErrors[item.field] = message;
        }
      } else if (!general) {
        general = message;
      }
    });

    return { fieldErrors, general };
  }

  function handleProfileError(status, body) {

    const { fieldErrors, general } = parseApiErrors(body);

    if (Object.keys(fieldErrors).length > 0) {
      showFieldErrors(fieldErrors);
    }

    if (general) {
      showProfileMessage(general, 'error');
    } else if (Object.keys(fieldErrors).length === 0) {
      showProfileMessage(
          `Could not update your profile (error ${status}). Please try again.`,
          'error'
      );
    }
  }

  // ============================================================
  // PROFILE MODAL — OPEN / CLOSE
  // ============================================================

  function openProfileModal() {

    if (!profileModal) {
      return;
    }

    if (!currentProfile) {
      showToast('Your profile is still loading. Please try again in a moment.');
      return;
    }

    clearTimeout(profileCloseTimer); // cancel a pending auto-close from a previous save

    lastFocusedBeforeProfileModal = document.activeElement;

    populateProfileForm();

    profileModal.hidden = false;

    if (profileDropdown) {
      profileDropdown.hidden = true;
    }

    if (profileFullname) {
      profileFullname.focus();
    }
  }

  function closeProfileModal() {

    if (!profileModal || profileModal.hidden) {
      return;
    }

    profileModal.hidden = true;

    clearProfileMessage();
    clearFieldErrors();

    if (
        lastFocusedBeforeProfileModal &&
        typeof lastFocusedBeforeProfileModal.focus === 'function'
    ) {
      lastFocusedBeforeProfileModal.focus();
    }
  }

  if (editProfileLink) {
    editProfileLink.addEventListener('click', event => {
      event.preventDefault();
      openProfileModal();
    });
  }

  if (sidebarProfileBtn) {
    sidebarProfileBtn.addEventListener('click', openProfileModal);
  }

  if (closeProfileBtn) {
    closeProfileBtn.addEventListener('click', closeProfileModal);
  }

  if (cancelProfileBtn) {
    cancelProfileBtn.addEventListener('click', closeProfileModal);
  }

  // Role toggle: update the hint and clear any role error.
  profileRoleRadios.forEach(radio => {
    radio.addEventListener('change', () => {
      updateRoleHint();
      clearFieldError('profileMode');
      clearProfileMessage();
    });
  });

  // Clear a field's error as soon as the user edits it.
  [
    ['fullName', profileFullname],
    ['email', profileEmail],
    ['phoneNumber', profilePhone]
  ].forEach(([name, input]) => {
    if (input) {
      input.addEventListener('input', () => {
        clearFieldError(name);
        clearProfileMessage();
      });
    }
  });

  // ============================================================
  // SAVE PROFILE  (PUT /api/auth/profile)
  // ============================================================

  function setProfileSaving(isSaving) {

    if (!saveProfileBtn) {
      return;
    }

    saveProfileBtn.disabled = isSaving;
    saveProfileBtn.textContent = isSaving ? 'Saving…' : 'Save profile';
  }

  if (profileForm) {

    profileForm.addEventListener('submit', async event => {

      event.preventDefault();

      if (saveProfileBtn && saveProfileBtn.disabled) {
        return; // a save is already in flight
      }

      clearProfileMessage();
      clearFieldErrors();

      const values = readProfileForm();
      const errors = validateProfileValues(values);

      if (Object.keys(errors).length > 0) {
        showFieldErrors(errors);
        return;
      }

      if (!hasProfileChanges(values)) {
        showProfileMessage('No changes to save.', 'info');
        return;
      }

      setProfileSaving(true);

      try {

        const response = await apiFetch(`${API_BASE}/api/auth/profile`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(values)
        });

        const body = await readJsonSafely(response);

        if (!response.ok) {
          handleProfileError(response.status, body);
          return;
        }

        // The JWT subject is the user's email, so when the email changes the
        // backend issues a fresh token. Swap it in before any further request.
        if (body && body.token) {
          localStorage.setItem(TOKEN_KEY, body.token);
        }

        const { token: _ignored, ...updatedProfile } = body || {};

        currentProfile = updatedProfile;

        renderProfile(currentProfile);
        populateProfileForm();

        showProfileMessage('Profile updated successfully.', 'success');
        showToast('Profile updated successfully.');

        profileCloseTimer = setTimeout(closeProfileModal, 900);

      } catch (error) {

        if (error.sessionExpired) {
          return; // apiFetch is already redirecting to the login page
        }

        console.error('Failed to update profile:', error);

        showProfileMessage(
            'Could not reach the server. Please check your connection and try again.',
            'error'
        );

      } finally {

        setProfileSaving(false);
      }
    });
  }

  // ============================================================
  // LOAD TASKS
  // ============================================================

  async function loadTasks() {

    tasksState = 'loading';
    renderSummaryCards();

    try {

      const response = await apiFetch(
          `${API_BASE}/api/schedule-items/me`
      );

      if (!response.ok) {
        throw new Error(
            `Server responded ${response.status}`
        );
      }

      allTasks =
          await response.json();

      tasksState = 'ready';

      await refreshConflicts();

      renderTasks();
      updateSummary();



    } catch (error) {

      if (error.sessionExpired) {
        return;
      }

      console.error(
          'Failed to load tasks:',
          error
      );

      tasksState = 'error';
      document.dispatchEvent(new CustomEvent('aalsikke:tasks-error')); // KAN-259
      renderSummaryCards();

      const failedText = 'Tasks unavailable';
      const greetingSummary = document.getElementById('greeting-summary');
      const profileMeta = document.getElementById('profile-meta');

      if (greetingSummary) {
        greetingSummary.textContent = failedText;
      }

      if (profileMeta) {
        profileMeta.textContent = failedText;
      }

      if (taskList) {

        taskList.innerHTML = `
          <li class="empty-state">
            Couldn't load tasks.
            Is the backend running on ${escapeHtml(API_BASE)}?
          </li>
        `;
      }
    }
  }

  // ============================================================
  // RENDER TASKS
  // ============================================================

  function renderTasks() {

    if (!taskList) {
      return;
    }

    const visibleTasks = getVisibleTasks();

    taskList.innerHTML = '';

    if (emptyState) {
      emptyState.hidden =
          visibleTasks.length !== 0;

      // Tell "you have no tasks" apart from "your filters hide every task".
      emptyState.textContent = allTasks.length === 0
          ? 'No tasks yet — add your first one above.'
          : 'No tasks match these filters. Change the role, status or view to see more.';
    }

    if (taskCount) {
      taskCount.textContent = allTasks.length === 0
          ? ''
          : `Showing ${visibleTasks.length} of ${allTasks.length} ` +
          `task${allTasks.length === 1 ? '' : 's'}`;
    }

    visibleTasks.forEach(task => {

      const li =
          document.createElement('li');

      const conflictPartners =
          conflictPartnersByTaskId.get(String(task.id)) || [];

      const hasConflict = conflictPartners.length > 0;

      li.className = `task-card${hasConflict ? ' is-conflict' : ''}`;
      li.dataset.id = task.id;

      const completed =
          String(task.status || '').toUpperCase()
          === 'COMPLETED';

      li.innerHTML = `
        <input
          type="checkbox"
          class="task-checkbox"
          aria-label="Mark ${escapeHtml(task.title)} complete"
          ${completed ? 'checked' : ''}
        >

        <div class="task-body">

          <p class="task-title">
            ${escapeHtml(task.title)}
          </p>

          <div class="task-meta">

            <span
              class="priority-dot ${priorityClass(task.priority)}"
            ></span>

            ${
          task.itemType
              ? escapeHtml(task.itemType) + ' &middot; '
              : ''
      }

            Due ${escapeHtml(
          formatDue(getTaskDate(task))
      )}

            ${
          hasConflict
              ? `<span class="conflict-badge" title="${escapeHtml(describeConflictPartners(conflictPartners))}">&#9888; Conflict</span>`
              : ''
      }

          </div>

        </div>

        <div class="task-actions">
          <button
            type="button"
            class="task-menu-btn"
            aria-label="Task actions"
            aria-expanded="false"
            title="Task actions"
          >
            &#8942;
          </button>
          <div class="task-menu" hidden>
            <button type="button" class="task-action-edit">Edit</button>
            <button type="button" class="task-action-delete">Delete</button>
          </div>
        </div>
      `;

      // --------------------------------------------------------
      // Checkbox
      // --------------------------------------------------------

      const checkbox =
          li.querySelector('.task-checkbox');

      if (checkbox) {
        checkbox.addEventListener('change', async event => {
          const nextStatus = event.target.checked ? 'COMPLETED' : 'PENDING';
          checkbox.disabled = true;

          try {
            const updatedTask = await updateTaskStatus(task.id, nextStatus);
            replaceTaskInState(updatedTask);
            renderTasks();
            updateSummary();
          } catch (error) {
            console.error('Failed to update task status:', error);
            event.target.checked = !event.target.checked;
            alert(`Could not update the task status.\n\n${error.message}`);
          } finally {
            checkbox.disabled = false;
          }
        });

        if (completed) {
          li.classList.add('is-complete');
        }
      }

      // --------------------------------------------------------
      // Edit button
      // --------------------------------------------------------

      const menuButton = li.querySelector('.task-menu-btn');
      const taskMenu = li.querySelector('.task-menu');
      const editButton = li.querySelector('.task-action-edit');
      const deleteButton = li.querySelector('.task-action-delete');

      if (menuButton && taskMenu) {
        menuButton.addEventListener('click', event => {
          event.stopPropagation();

          document.querySelectorAll('.task-menu').forEach(menu => {
            if (menu !== taskMenu) menu.hidden = true;
          });

          taskMenu.hidden = !taskMenu.hidden;
          menuButton.setAttribute('aria-expanded', String(!taskMenu.hidden));
        });
      }

      if (editButton) {
        editButton.addEventListener('click', () => {
          taskMenu.hidden = true;
          menuButton?.setAttribute('aria-expanded', 'false');
          openEditModal(task);
        });
      }

      if (deleteButton) {
        deleteButton.addEventListener('click', async () => {
          taskMenu.hidden = true;
          menuButton?.setAttribute('aria-expanded', 'false');
          await deleteTask(task.id);
        });
      }

      taskList.appendChild(li);
    });
  }

  // ============================================================
  // SUMMARY CARDS (overall totals + Student / Work)
  // ============================================================

  const ROLE_CARDS = {
    STUDENT: {
      title: 'Student',
      blurb: 'Assignments, exams, classes and study',
      icon: '&#127891;'
    },
    WORK: {
      title: 'Work',
      blurb: 'Shifts and work tasks',
      icon: '&#128188;'
    }
  };

  /*
   * Counts a list of tasks into the same three buckets the Status filter
   * uses, so pending + completed + overdue always equals total.
   */
  function summarizeTasks(tasks) {
    const summary = { total: tasks.length, pending: 0, completed: 0, overdue: 0 };

    tasks.forEach(task => {
      summary[taskStatus(task).toLowerCase()] += 1;
    });

    return summary;
  }

  function completionPercent(summary) {
    return summary.total === 0
        ? 0
        : Math.round((summary.completed / summary.total) * 100);
  }

  /*
   * Which role cards to show. The profile mode decides (Student, Work, or both
   * for Dual), but a role that already has tasks is never hidden - otherwise
   * switching from Dual to Student would make the user's work tasks vanish
   * from the summary while they still appear in the list.
   */
  function rolesToShow() {
    const mode = currentProfile && currentProfile.profileMode;
    const roles = new Set(
        mode === 'STUDENT' ? ['STUDENT']
            : mode === 'WORK' ? ['WORK']
                : ['STUDENT', 'WORK']
    );

    Object.keys(ROLE_CARDS).forEach(role => {
      if (allTasks.some(task => roleForTask(task) === role)) {
        roles.add(role);
      }
    });

    return Object.keys(ROLE_CARDS).filter(role => roles.has(role));
  }

  function metricHtml(key, label, value) {
    const alert = key === 'overdue' && value > 0 ? ' is-alert' : '';

    return `
      <div class="metric metric--${key}${alert}" data-metric="${key}">
        <dt>${label}</dt>
        <dd>${value}</dd>
      </div>`;
  }

  function roleCardHtml(role) {
    const meta = ROLE_CARDS[role];
    const summary = summarizeTasks(
        allTasks.filter(task => roleForTask(task) === role)
    );
    const percent = completionPercent(summary);
    const titleId = `role-card-${role.toLowerCase()}-title`;
    const progressText = `${summary.completed} of ${summary.total} completed`;

    return `
      <article class="card role-card role-card--${role.toLowerCase()}"
               data-role="${role}" aria-labelledby="${titleId}">
        <header class="role-card-head">
          <span class="role-card-icon" aria-hidden="true">${meta.icon}</span>
          <div class="role-card-heading">
            <h2 id="${titleId}">${meta.title}</h2>
            <p>${meta.blurb}</p>
          </div>
          <p class="role-card-total">
            <strong data-metric="total">${summary.total}</strong>
            <span>${summary.total === 1 ? 'task' : 'tasks'}</span>
          </p>
        </header>

        <dl class="metric-list">
          ${metricHtml('pending', 'Pending', summary.pending)}
          ${metricHtml('overdue', 'Overdue', summary.overdue)}
          ${metricHtml('completed', 'Completed', summary.completed)}
        </dl>

        <div class="progress" role="progressbar"
             aria-label="${meta.title} tasks completed"
             aria-valuemin="0" aria-valuemax="100"
             aria-valuenow="${percent}" aria-valuetext="${progressText}">
          <span style="width: ${percent}%"></span>
        </div>
        <p class="role-card-foot">${
        summary.total === 0
            ? `No ${meta.title.toLowerCase()} tasks yet`
            : `${progressText} (${percent}%)`
    }</p>
      </article>`;
  }

  function overallSkeletonHtml() {
    return Array.from({ length: 4 }, () => `
      <div class="dashboard-stat-card is-skeleton" aria-hidden="true">
        <span class="skeleton skeleton-line"></span>
        <strong class="skeleton skeleton-number"></strong>
      </div>`).join('');
  }

  function roleSkeletonHtml(count) {
    return Array.from({ length: count }, () => `
      <div class="card role-card is-skeleton" aria-hidden="true">
        <div class="role-card-head">
          <span class="skeleton skeleton-icon"></span>
          <div class="role-card-heading">
            <span class="skeleton skeleton-line skeleton-title"></span>
            <span class="skeleton skeleton-line"></span>
          </div>
        </div>
        <div class="metric-list">
          <span class="skeleton skeleton-block"></span>
          <span class="skeleton skeleton-block"></span>
          <span class="skeleton skeleton-block"></span>
        </div>
        <span class="skeleton skeleton-line skeleton-bar"></span>
      </div>`).join('');
  }

  function renderOverallCards() {
    const cards = document.getElementById('dashboard-cards');

    if (!cards) {
      return;
    }

    if (tasksState === 'loading') {
      cards.innerHTML = overallSkeletonHtml();
      return;
    }

    if (tasksState === 'error') {
      cards.innerHTML = `
        <div class="summary-error" role="alert">
          <p>Couldn't load your task summary.</p>
          <button type="button" class="link-btn" data-action="retry-summary">Try again</button>
        </div>`;
      return;
    }

    const summary = summarizeTasks(allTasks);

    cards.innerHTML = `
      <div class="dashboard-stat-card"><span>Total tasks</span><strong>${summary.total}</strong></div>
      <div class="dashboard-stat-card"><span>Pending</span><strong>${summary.pending}</strong></div>
      <div class="dashboard-stat-card"><span>Completed</span><strong>${summary.completed}</strong></div>
      <div class="dashboard-stat-card${summary.overdue > 0 ? ' is-alert' : ''}"><span>Overdue</span><strong>${summary.overdue}</strong></div>
    `;
  }

  function renderRoleCards() {
    const roleCards = document.getElementById('role-cards');

    if (!roleCards) {
      return;
    }

    if (tasksState === 'error') {
      roleCards.innerHTML = '';
      return;
    }

    // Role cards also need the profile (its mode decides which cards show).
    if (tasksState === 'loading' || profileState === 'loading') {
      const known = currentProfile && currentProfile.profileMode;
      roleCards.innerHTML = roleSkeletonHtml(
          known === 'STUDENT' || known === 'WORK' ? 1 : 2
      );
      return;
    }

    roleCards.innerHTML = rolesToShow().map(roleCardHtml).join('');
  }

  function renderSummaryCards() {
    const summary = document.getElementById('dashboard-summary');

    if (summary) {
      summary.setAttribute(
          'aria-busy',
          String(tasksState === 'loading' ||
              (tasksState === 'ready' && profileState === 'loading'))
      );
    }

    renderOverallCards();
    renderRoleCards();
  }

  const dashboardSummary = document.getElementById('dashboard-summary');

  if (dashboardSummary) {
    dashboardSummary.addEventListener('click', event => {
      if (event.target.closest('[data-action="retry-summary"]')) {
        loadTasks();
      }
    });
  }

  // ============================================================
  // UPDATE DASHBOARD SUMMARY
  // ============================================================

  function updateSummary() {

    const now = new Date();

    const greetingSummary =
        document.getElementById('greeting-summary');

    const profileMeta =
        document.getElementById('profile-meta');

    const dueToday =
        allTasks.filter(task =>
            isToday(getTaskDate(task))
        ).length;

    const overdue =
        allTasks.filter(task =>
            isOverdue(task)
        ).length;

    const summaryText =
        `${dueToday} task${dueToday === 1 ? '' : 's'} due today, ` +
        `${overdue} overdue`;

    if (greetingSummary) {
      greetingSummary.textContent =
          summaryText;
    }

    if (profileMeta) {
      profileMeta.textContent = summaryText;
    }

    renderSummaryCards();

    // KAN-259: every task change (load, create, edit, complete, delete) ends
    // here, so this is the one place that tells the Tasks page to re-render.
    document.dispatchEvent(new CustomEvent('aalsikke:tasks-loaded'));
  }

  // ============================================================
  // CREATE TASK
  // ============================================================

  if (taskForm) {

    taskForm.addEventListener(
        'submit',
        async event => {

          event.preventDefault();

          const title =
              document.getElementById('task-title')
                  ?.value.trim() || '';

          const due =
              document.getElementById('task-due')
                  ?.value || '';

          const priority =
              document.getElementById('task-priority')
                  ?.value || 'MEDIUM';

          const itemType =
              document.getElementById('task-category')
                  ?.value || 'PERSONAL';

          // Validation
          if (!title) {

            alert('Task title is required.');
            return;
          }

          if (title.length > 255) {

            alert(
                'Task title cannot be longer than 255 characters.'
            );
            return;
          }

          if (!due) {

            alert('Deadline is required.');
            return;
          }

          const deadline =
              toISOStringFromLocal(due);

          if (!deadline) {

            alert('Please enter a valid deadline.');
            return;
          }

          if (new Date(deadline) <= new Date()) {

            alert(
                'Deadline must be in the future.'
            );
            return;
          }

          /*
           * ScheduleItemRequest requires:
           * title
           * itemType
           * startTime
           * endTime
           *
           * We use the deadline as the start time and
           * 30 minutes later as the end time.
           */

          const startTime = deadline;

          const endTime =
              new Date(
                  new Date(deadline).getTime() +
                  30 * 60 * 1000
              ).toISOString();

          const payload = {
            title,
            itemType,
            priority,
            startTime,
            endTime,
            deadline,
            isRecurring: false
          };

          saveTaskBtn.disabled = true;
          saveTaskBtn.textContent =
              'Adding…';

          try {

            const response =
                await apiFetch(
                    `${API_BASE}/api/schedule-items`,
                    {
                      method: 'POST',
                      headers: {
                        'Content-Type':
                            'application/json'
                      },
                      body: JSON.stringify(payload)
                    }
                );

            if (!response.ok) {

              let message =
                  `Server responded ${response.status}`;

              try {

                const data =
                    await response.json();

                message =
                    data.message ||
                    data.error ||
                    message;

              } catch (_) {}

              throw new Error(message);
            }

            const createdTask =
                await response.json();

            allTasks.push(createdTask);

            await refreshConflicts();

            renderTasks();
            updateSummary();

            announceConflictsIfAny(createdTask.id);

            taskForm.reset();
            taskForm.hidden = true;

          } catch (error) {

            console.error(
                'Failed to create task:',
                error
            );

            alert(
                `Could not save the task.\n\n${error.message}`
            );

          } finally {

            saveTaskBtn.disabled = false;
            saveTaskBtn.textContent =
                '+ Add task';
          }
        }
    );
  }

  // ============================================================
  // TASK STATUS
  // ============================================================

  async function updateTaskStatus(id, status) {
    const response = await apiFetch(
        `${API_BASE}/api/schedule-items/${id}/status`,
        {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ status })
        }
    );

    if (response.status === 403) {
      throw new Error('You do not have permission to update this task.');
    }

    if (!response.ok) {
      let message = `Server responded ${response.status}`;
      try {
        const data = await response.json();
        message = data.message || data.error || message;
      } catch (_) {}
      throw new Error(message);
    }

    return response.json();
  }

  function replaceTaskInState(updatedTask) {
    const index = allTasks.findIndex(
        task => String(task.id) === String(updatedTask.id)
    );

    if (index !== -1) {
      allTasks[index] = updatedTask;
    }
  }

  // ============================================================
  // DELETE TASK
  // ============================================================

  async function deleteTask(id) {

    const confirmed =
        window.confirm(
            'Delete this task?'
        );

    if (!confirmed) {
      return;
    }

    try {

      const response =
          await apiFetch(
              `${API_BASE}/api/schedule-items/${id}`,
              {
                method: 'DELETE'
              }
          );

      if (response.status === 403) {

        alert(
            'You do not have permission to delete this task.'
        );

        return;
      }

      if (
          !response.ok &&
          response.status !== 204
      ) {

        throw new Error(
            `Server responded ${response.status}`
        );
      }

      allTasks =
          allTasks.filter(
              task => task.id !== id
          );

      renderTasks();
      updateSummary();

    } catch (error) {

      console.error(
          'Failed to delete task:',
          error
      );

      alert(
          'Could not delete the task. Please check the console.'
      );
    }
  }

  // ============================================================
  // EDIT TASK MODAL
  // ============================================================

  function openEditModal(task) {

    if (!taskModal || !task) {
      return;
    }

    editTaskId.value =
        task.id || '';

    editTaskTitle.value =
        task.title || '';

    editTaskDescription.value =
        task.description || '';

    editTaskDeadline.value =
        toDateTimeLocal(
            task.deadline || task.startTime
        );

    editTaskPriority.value =
        task.priority || 'MEDIUM';

    editTaskCategory.value =
        task.itemType || 'PERSONAL';

    editTaskStatus.value =
        task.status || 'PENDING';

    /*
     * Status is displayed, but the current backend PUT request
     * does not accept/update status.
     */
    editTaskStatus.disabled = true;
    editTaskStatus.title = 'Use the task checkbox to change completion status.';

    taskModal.hidden = false;

    editTaskTitle.focus();
  }

  function closeEditModal() {

    if (!taskModal) {
      return;
    }

    taskModal.hidden = true;
  }

  if (closeTaskBtn) {
    closeTaskBtn.addEventListener(
        'click',
        closeEditModal
    );
  }

  if (cancelEditBtn) {
    cancelEditBtn.addEventListener(
        'click',
        closeEditModal
    );
  }

  // ============================================================
  // EDIT TASK VALIDATION
  // ============================================================

  function validateEditTask() {

    const title =
        editTaskTitle.value.trim();

    const description =
        editTaskDescription.value.trim();

    const deadline =
        editTaskDeadline.value;

    if (!title) {

      alert(
          'Task title is required.'
      );

      editTaskTitle.focus();

      return false;
    }

    if (title.length > 255) {

      alert(
          'Task title cannot be longer than 255 characters.'
      );

      editTaskTitle.focus();

      return false;
    }

    if (description.length > 2000) {

      alert(
          'Description cannot be longer than 2000 characters.'
      );

      editTaskDescription.focus();

      return false;
    }

    if (!deadline) {

      alert(
          'Deadline is required.'
      );

      editTaskDeadline.focus();

      return false;
    }

    const deadlineDate =
        new Date(deadline);

    if (Number.isNaN(deadlineDate.getTime())) {

      alert(
          'Please enter a valid deadline.'
      );

      editTaskDeadline.focus();

      return false;
    }

    if (deadlineDate <= new Date()) {

      alert(
          'Deadline must be in the future.'
      );

      editTaskDeadline.focus();

      return false;
    }

    return true;
  }

  // ============================================================
  // UPDATE TASK
  // ============================================================

  if (editTaskForm) {

    editTaskForm.addEventListener(
        'submit',
        async event => {

          event.preventDefault();

          if (!validateEditTask()) {
            return;
          }

          const id =
              editTaskId.value;

          const existingTask =
              allTasks.find(
                  task => String(task.id) === String(id)
              );

          if (!existingTask) {

            alert(
                'The selected task could not be found.'
            );

            return;
          }

          const deadline =
              toISOStringFromLocal(
                  editTaskDeadline.value
              );

          if (!deadline) {

            alert(
                'Invalid deadline.'
            );

            return;
          }

          /*
           * IMPORTANT:
           *
           * The backend ScheduleItemRequest requires:
           *   title
           *   itemType
           *   startTime
           *   endTime
           *
           * Therefore we preserve the existing start/end times
           * instead of sending only title/role/status.
           */

          const payload = {
            title:
                editTaskTitle.value.trim(),

            description:
                editTaskDescription.value.trim(),

            itemType:
            editTaskCategory.value,

            priority:
            editTaskPriority.value,

            startTime:
            existingTask.startTime,

            endTime:
            existingTask.endTime,

            deadline,

            isRecurring:
                existingTask.isRecurring ?? false
          };

          /*
           * Preserve relationships if they already exist.
           * These are accepted by ScheduleItemRequest.
           */

          if (existingTask.course?.id) {
            payload.courseId =
                existingTask.course.id;
          }

          if (existingTask.workplace?.id) {
            payload.workplaceId =
                existingTask.workplace.id;
          }

          updateTaskBtn.disabled = true;
          updateTaskBtn.textContent =
              'Saving…';

          try {

            const response =
                await apiFetch(
                    `${API_BASE}/api/schedule-items/${id}`,
                    {
                      method: 'PUT',
                      headers: {
                        'Content-Type':
                            'application/json'
                      },
                      body: JSON.stringify(payload)
                    }
                );

            if (response.status === 403) {

              alert(
                  'You do not have permission to update this task.'
              );

              return;
            }

            if (!response.ok) {

              let message =
                  `Server responded ${response.status}`;

              try {

                const data =
                    await response.json();

                if (data.message) {
                  message =
                      data.message;
                } else if (data.error) {
                  message =
                      data.error;
                }

                /*
                 * Spring validation can sometimes return
                 * field-level information.
                 */
                if (data.errors) {

                  const validationErrors =
                      Object.values(data.errors)
                          .join('\n');

                  if (validationErrors) {
                    message =
                        validationErrors;
                  }
                }

              } catch (_) {}

              throw new Error(message);
            }

            const updatedTask =
                await response.json();

            /*
             * Replace the old task in local state.
             */
            const index =
                allTasks.findIndex(
                    task =>
                        String(task.id) === String(id)
                );

            if (index !== -1) {

              allTasks[index] =
                  updatedTask;
            }

            await refreshConflicts();

            renderTasks();
            updateSummary();

            closeEditModal();

            announceConflictsIfAny(updatedTask.id);

          } catch (error) {

            console.error(
                'Failed to update task:',
                error
            );

            alert(
                `Could not update the task.\n\n${error.message}`
            );

          } finally {

            updateTaskBtn.disabled = false;
            updateTaskBtn.textContent =
                'Save changes';
          }
        }
    );
  }

  // ============================================================
  // MODAL BACKDROP CLICK
  // ============================================================

  if (profileModal) {

    profileModal.addEventListener(
        'click',
        event => {

          if (event.target === profileModal) {
            closeProfileModal();
          }
        }
    );
  }

  if (taskModal) {

    taskModal.addEventListener(
        'click',
        event => {

          if (event.target === taskModal) {
            closeEditModal();
          }
        }
    );
  }

  // ============================================================
  // CHANGE PASSWORD MODAL (KAN-242)
  // PUT /api/auth/change-password
  // ============================================================

  const changePasswordLink = document.getElementById('change-password-link');
  const passwordModal = document.getElementById('password-modal');
  const passwordForm = document.getElementById('password-form');
  const closePasswordBtn = document.getElementById('close-password-modal');
  const cancelPasswordBtn = document.getElementById('cancel-password-btn');
  const savePasswordBtn = document.getElementById('save-password-btn');

  const currentPasswordInput = document.getElementById('current-password');
  const newPasswordInput = document.getElementById('new-password');
  const confirmNewPasswordInput = document.getElementById('confirm-new-password');
  const passwordStrengthMeter = document.getElementById('password-strength-meter');
  const newPasswordStrengthHint = document.getElementById('new-password-strength-hint');
  const passwordMessage = document.getElementById('password-message');

  // Field name (as sent to / returned by the API) -> wrapper element id.
  const PASSWORD_FIELD_IDS = {
    currentPassword: 'field-current-password',
    newPassword: 'field-new-password',
    confirmPassword: 'field-confirm-new-password'
  };

  const PASSWORD_FIELD_ORDER = ['currentPassword', 'newPassword', 'confirmPassword'];

  let lastFocusedBeforePasswordModal = null;
  let passwordCloseTimer = null;

  function getPasswordFieldInput(name) {
    if (name === 'currentPassword') return currentPasswordInput;
    if (name === 'newPassword') return newPasswordInput;
    if (name === 'confirmPassword') return confirmNewPasswordInput;
    return null;
  }

  function setPasswordFieldError(name, message) {
    const wrapper = document.getElementById(PASSWORD_FIELD_IDS[name]);
    if (!wrapper) return;
    wrapper.classList.add('is-error');
    const hint = wrapper.querySelector('.hint.error-text');
    if (hint) hint.textContent = message;
    const input = getPasswordFieldInput(name);
    if (input) input.setAttribute('aria-invalid', 'true');
  }

  function clearPasswordFieldError(name) {
    const wrapper = document.getElementById(PASSWORD_FIELD_IDS[name]);
    if (!wrapper) return;
    wrapper.classList.remove('is-error');
    const hint = wrapper.querySelector('.hint.error-text');
    if (hint) hint.textContent = '';
    const input = getPasswordFieldInput(name);
    if (input) input.removeAttribute('aria-invalid');
  }

  function clearPasswordFieldErrors() {
    Object.keys(PASSWORD_FIELD_IDS).forEach(clearPasswordFieldError);
  }

  function showPasswordFieldErrors(errors) {
    Object.entries(errors).forEach(([name, message]) => setPasswordFieldError(name, message));
    const firstInvalid = PASSWORD_FIELD_ORDER.find(name => errors[name]);
    const input = firstInvalid ? getPasswordFieldInput(firstInvalid) : null;
    if (input) input.focus();
  }

  function showPasswordMessage(message, type) {
    if (!passwordMessage) return;
    passwordMessage.textContent = message;
    passwordMessage.classList.remove('is-success', 'is-error', 'is-info');
    if (type) passwordMessage.classList.add(`is-${type}`);
  }

  function clearPasswordMessage() {
    showPasswordMessage('', null);
  }

  // Same 4-point scoring register.js uses for the sign-up password field.
  function scoreNewPassword(value) {
    let score = 0;
    if (value.length >= 8) score++;
    if (/[A-Z]/.test(value)) score++;
    if (/[0-9]/.test(value)) score++;
    if (/[^A-Za-z0-9]/.test(value)) score++;
    return score;
  }

  function updatePasswordStrengthMeter() {
    const value = newPasswordInput ? newPasswordInput.value : '';
    if (!passwordStrengthMeter || !newPasswordStrengthHint) return;

    passwordStrengthMeter.classList.remove('strength-1', 'strength-2', 'strength-3');

    if (!value) {
      newPasswordStrengthHint.style.display = 'none';
      return;
    }

    const score = scoreNewPassword(value);

    if (score <= 1) {
      passwordStrengthMeter.classList.add('strength-1');
      newPasswordStrengthHint.textContent = 'Too weak. Add length, a number, and a symbol.';
    } else if (score <= 3) {
      passwordStrengthMeter.classList.add('strength-2');
      newPasswordStrengthHint.textContent = 'Good, but add a special symbol to make it bulletproof.';
    } else {
      passwordStrengthMeter.classList.add('strength-3');
    }

    newPasswordStrengthHint.style.display = score <= 3 ? 'block' : 'none';
  }

  function validatePasswordValues(values) {
    const errors = {};

    if (!values.currentPassword) {
      errors.currentPassword = 'Current password is required.';
    }

    if (!values.newPassword) {
      errors.newPassword = 'New password is required.';
    } else if (values.newPassword.length < 8) {
      errors.newPassword = 'New password must be at least 8 characters.';
    } else if (values.currentPassword && values.newPassword === values.currentPassword) {
      errors.newPassword = 'New password must be different from your current password.';
    }

    if (!values.confirmPassword) {
      errors.confirmPassword = 'Please confirm your new password.';
    } else if (values.confirmPassword !== values.newPassword) {
      errors.confirmPassword = 'Passwords do not match.';
    }

    return errors;
  }

  // The backend answers with either
  //   400 -> { field, message }            (wrong current password)
  //   400 -> [ { field, message }, ... ]   (bean validation)
  //   4xx -> { message } or { error }      (everything else)
  function parsePasswordApiErrors(body) {
    const fieldErrors = {};
    let general = '';

    const items = Array.isArray(body) ? body : (body ? [body] : []);

    items.forEach(item => {
      if (!item || typeof item !== 'object') return;
      const message = item.message || item.error;
      if (!message) return;

      if (item.field && PASSWORD_FIELD_IDS[item.field]) {
        if (!fieldErrors[item.field]) fieldErrors[item.field] = message;
      } else if (!general) {
        general = message;
      }
    });

    return { fieldErrors, general };
  }

  function handlePasswordError(status, body) {
    const { fieldErrors, general } = parsePasswordApiErrors(body);

    if (Object.keys(fieldErrors).length > 0) {
      showPasswordFieldErrors(fieldErrors);
    }

    if (general) {
      showPasswordMessage(general, 'error');
    } else if (Object.keys(fieldErrors).length === 0) {
      showPasswordMessage(`Could not update your password (error ${status}). Please try again.`, 'error');
    }
  }

  function resetPasswordForm() {
    if (passwordForm) passwordForm.reset();
    clearPasswordFieldErrors();
    clearPasswordMessage();
    if (passwordStrengthMeter) passwordStrengthMeter.classList.remove('strength-1', 'strength-2', 'strength-3');
    if (newPasswordStrengthHint) newPasswordStrengthHint.style.display = 'none';
  }

  function openPasswordModal() {
    if (!passwordModal) return;

    clearTimeout(passwordCloseTimer);
    resetPasswordForm();

    lastFocusedBeforePasswordModal = document.activeElement;

    passwordModal.hidden = false;

    if (profileDropdown) profileDropdown.hidden = true;

    if (currentPasswordInput) currentPasswordInput.focus();
  }

  function closePasswordModal() {
    if (!passwordModal || passwordModal.hidden) return;

    passwordModal.hidden = true;
    resetPasswordForm();

    if (lastFocusedBeforePasswordModal && typeof lastFocusedBeforePasswordModal.focus === 'function') {
      lastFocusedBeforePasswordModal.focus();
    }
  }

  if (changePasswordLink) {
    changePasswordLink.addEventListener('click', event => {
      event.preventDefault();
      openPasswordModal();
    });
  }

  if (closePasswordBtn) closePasswordBtn.addEventListener('click', closePasswordModal);
  if (cancelPasswordBtn) cancelPasswordBtn.addEventListener('click', closePasswordModal);

  if (passwordModal) {
    passwordModal.addEventListener('click', event => {
      if (event.target === passwordModal) closePasswordModal();
    });
  }

  if (newPasswordInput) {
    newPasswordInput.addEventListener('input', () => {
      updatePasswordStrengthMeter();
      clearPasswordFieldError('newPassword');
      clearPasswordMessage();
    });
  }

  [
    ['currentPassword', currentPasswordInput],
    ['confirmPassword', confirmNewPasswordInput]
  ].forEach(([name, input]) => {
    if (input) {
      input.addEventListener('input', () => {
        clearPasswordFieldError(name);
        clearPasswordMessage();
      });
    }
  });

  function setPasswordSaving(isSaving) {
    if (!savePasswordBtn) return;
    savePasswordBtn.disabled = isSaving;
    savePasswordBtn.textContent = isSaving ? 'Updating…' : 'Update password';
  }

  if (passwordForm) {
    passwordForm.addEventListener('submit', async event => {
      event.preventDefault();

      if (savePasswordBtn && savePasswordBtn.disabled) return;

      clearPasswordMessage();
      clearPasswordFieldErrors();

      const values = {
        currentPassword: currentPasswordInput ? currentPasswordInput.value : '',
        newPassword: newPasswordInput ? newPasswordInput.value : '',
        confirmPassword: confirmNewPasswordInput ? confirmNewPasswordInput.value : ''
      };

      const errors = validatePasswordValues(values);

      if (Object.keys(errors).length > 0) {
        showPasswordFieldErrors(errors);
        return;
      }

      setPasswordSaving(true);

      try {
        const response = await apiFetch(`${API_BASE}/api/auth/change-password`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            currentPassword: values.currentPassword,
            newPassword: values.newPassword
          })
        });

        const body = await readJsonSafely(response);

        if (!response.ok) {
          handlePasswordError(response.status, body);
          return;
        }

        showPasswordMessage('Password updated successfully.', 'success');
        showToast('Password updated successfully.');

        passwordCloseTimer = setTimeout(closePasswordModal, 900);

      } catch (error) {

        if (error.sessionExpired) return;

        console.error('Failed to update password:', error);
        showPasswordMessage(
            'Could not reach the server. Please check your connection and try again.',
            'error'
        );

      } finally {
        setPasswordSaving(false);
      }
    });
  }

  // ============================================================
  // NOTIFICATION PREFERENCES MODAL (KAN-244)
  // GET/PUT /api/notification-preferences/me
  // ============================================================

  const notifPrefsLink = document.getElementById('notification-prefs-link');
  const settingsNavLink = document.getElementById('settings-nav-link');
  const settingsBottomLink = document.getElementById('settings-bottom-link');
  const notifPrefsModal = document.getElementById('notif-prefs-modal');
  const notifPrefsForm = document.getElementById('notif-prefs-form');
  const closeNotifPrefsBtn = document.getElementById('close-notif-prefs-modal');
  const cancelNotifPrefsBtn = document.getElementById('cancel-notif-prefs-btn');
  const saveNotifPrefsBtn = document.getElementById('save-notif-prefs-btn');
  const notifPrefsMessage = document.getElementById('notif-prefs-message');

  const prefToggles = {
    deadlineReminders: document.getElementById('pref-deadline-reminders'),
    overdueAlerts: document.getElementById('pref-overdue-alerts'),
    scheduleConflicts: document.getElementById('pref-schedule-conflicts'),
    weeklyReviewReady: document.getElementById('pref-weekly-review-ready')
  };

  let lastFocusedBeforeNotifPrefsModal = null;

  function showNotifPrefsMessage(message, type) {
    if (!notifPrefsMessage) return;
    notifPrefsMessage.textContent = message;
    notifPrefsMessage.classList.remove('is-success', 'is-error', 'is-info');
    if (type) notifPrefsMessage.classList.add(`is-${type}`);
  }

  function setNotifPrefsTogglesDisabled(disabled) {
    Object.values(prefToggles).forEach(toggle => {
      if (toggle) toggle.disabled = disabled;
    });
  }

  function applyPreferencesToToggles(preferences) {
    Object.entries(prefToggles).forEach(([key, toggle]) => {
      if (toggle) toggle.checked = Boolean(preferences && preferences[key]);
    });
  }

  async function loadNotificationPreferences() {
    setNotifPrefsTogglesDisabled(true);
    showNotifPrefsMessage('Loading your preferences…', 'info');

    try {
      const response = await apiFetch(`${API_BASE}/api/notification-preferences/me`);

      if (!response.ok) {
        throw new Error(`Server responded ${response.status}`);
      }

      const preferences = await response.json();
      applyPreferencesToToggles(preferences);
      showNotifPrefsMessage('', null);

    } catch (error) {

      if (error.sessionExpired) return;

      console.error('Failed to load notification preferences:', error);
      showNotifPrefsMessage('Could not load your preferences. Please try again.', 'error');

    } finally {
      setNotifPrefsTogglesDisabled(false);
    }
  }

  function openNotifPrefsModal() {
    if (!notifPrefsModal) return;

    lastFocusedBeforeNotifPrefsModal = document.activeElement;

    notifPrefsModal.hidden = false;

    if (profileDropdown) profileDropdown.hidden = true;

    loadNotificationPreferences();
  }

  function closeNotifPrefsModal() {
    if (!notifPrefsModal || notifPrefsModal.hidden) return;

    notifPrefsModal.hidden = true;
    showNotifPrefsMessage('', null);

    if (lastFocusedBeforeNotifPrefsModal && typeof lastFocusedBeforeNotifPrefsModal.focus === 'function') {
      lastFocusedBeforeNotifPrefsModal.focus();
    }
  }

  if (notifPrefsLink) {
    notifPrefsLink.addEventListener('click', event => {
      event.preventDefault();
      openNotifPrefsModal();
    });
  }
  const settingsChangePasswordBtn = document.getElementById('settings-change-password-btn');
  const settingsNotifPrefsBtn = document.getElementById('settings-notif-prefs-btn');

  if (settingsChangePasswordBtn) {
    settingsChangePasswordBtn.addEventListener('click', openPasswordModal);
  }

  if (settingsNotifPrefsBtn) {
    settingsNotifPrefsBtn.addEventListener('click', openNotifPrefsModal);
  }

  if (closeNotifPrefsBtn) closeNotifPrefsBtn.addEventListener('click', closeNotifPrefsModal);
  if (cancelNotifPrefsBtn) cancelNotifPrefsBtn.addEventListener('click', closeNotifPrefsModal);

  if (notifPrefsModal) {
    notifPrefsModal.addEventListener('click', event => {
      if (event.target === notifPrefsModal) closeNotifPrefsModal();
    });
  }

  function setNotifPrefsSaving(isSaving) {
    if (!saveNotifPrefsBtn) return;
    saveNotifPrefsBtn.disabled = isSaving;
    saveNotifPrefsBtn.textContent = isSaving ? 'Saving…' : 'Save preferences';
  }

  if (notifPrefsForm) {
    notifPrefsForm.addEventListener('submit', async event => {
      event.preventDefault();

      if (saveNotifPrefsBtn && saveNotifPrefsBtn.disabled) return;

      showNotifPrefsMessage('', null);
      setNotifPrefsSaving(true);

      const body = {};
      Object.entries(prefToggles).forEach(([key, toggle]) => {
        body[key] = toggle ? toggle.checked : true;
      });

      try {
        const response = await apiFetch(`${API_BASE}/api/notification-preferences/me`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });

        const responseBody = await readJsonSafely(response);

        if (!response.ok) {
          const message = (responseBody && (responseBody.message || responseBody.error))
              || `Could not save your preferences (error ${response.status}).`;
          showNotifPrefsMessage(message, 'error');
          return;
        }

        if (responseBody) applyPreferencesToToggles(responseBody);

        showNotifPrefsMessage('Preferences saved.', 'success');
        showToast('Notification preferences saved.');

      } catch (error) {

        if (error.sessionExpired) return;

        console.error('Failed to save notification preferences:', error);
        showNotifPrefsMessage(
            'Could not reach the server. Please check your connection and try again.',
            'error'
        );

      } finally {
        setNotifPrefsSaving(false);
      }
    });
  }
  // ============================================================
  // REMINDERS BELL (KAN-236)
  // GET /api/reminders/due, PATCH /api/reminders/{id}/trigger
  // ============================================================

  function renderNotifList(reminders) {
    if (!notifList) return;
    notifList.innerHTML = '';

    if (notifEmpty) {
      notifEmpty.hidden = reminders.length !== 0;
    }

    reminders.forEach(reminder => {
      const li = document.createElement('li');
      li.className = 'notif-item';
      li.dataset.id = reminder.id;

      const title = reminder.scheduleItemTitle || 'Reminder';

      li.innerHTML = `
        <div class="notif-item-body">
          <p class="notif-item-title">${escapeHtml(title)}</p>
          <span class="notif-item-time">Due ${escapeHtml(formatDue(reminder.remindAt))}</span>
        </div>
        <button type="button" class="notif-dismiss-btn" aria-label="Dismiss reminder">&#10003;</button>
      `;

      const dismissBtn = li.querySelector('.notif-dismiss-btn');
      if (dismissBtn) {
        dismissBtn.addEventListener('click', () => dismissReminder(reminder.id, li));
      }

      notifList.appendChild(li);
    });
  }

  function updateNotifBadge() {
    if (notifBadge) {
      notifBadge.hidden = dueReminders.length === 0;
    }
  }

  async function loadDueReminders() {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders/due`);

      if (!response.ok) {
        throw new Error(`Server responded ${response.status}`);
      }

      dueReminders = await response.json();
      renderNotifList(dueReminders);
      updateNotifBadge();

    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to load due reminders:', error);
    }
  }

  async function dismissReminder(id, li) {
    try {
      const response = await apiFetch(`${API_BASE}/api/reminders/${id}/trigger`, { method: 'PATCH' });

      if (!response.ok && response.status !== 204) {
        throw new Error(`Server responded ${response.status}`);
      }

      dueReminders = dueReminders.filter(reminder => String(reminder.id) !== String(id));
      li.remove();
      updateNotifBadge();

      if (notifEmpty) {
        notifEmpty.hidden = notifList.children.length !== 0;
      }

    } catch (error) {
      if (error.sessionExpired) return;
      console.error('Failed to dismiss reminder:', error);
      showToast('Could not dismiss the reminder.');
    }
  }

  if (notifBtn && notifDropdown) {
    notifBtn.addEventListener('click', event => {
      event.stopPropagation();
      notifDropdown.hidden = !notifDropdown.hidden;
    });
  }

  document.addEventListener('click', event => {
    if (notifDropdown && !event.target.closest('#notif-menu')) {
      notifDropdown.hidden = true;
    }
  });

  // ============================================================
  // ESCAPE KEY
  // ============================================================

  document.addEventListener(
      'keydown',
      event => {

        if (event.key !== 'Escape') {
          return;
        }

        if (
            profileModal &&
            !profileModal.hidden
        ) {
          closeProfileModal();
        }

        if (
            taskModal &&
            !taskModal.hidden
        ) {
          closeEditModal();
        }

        if (
            passwordModal &&
            !passwordModal.hidden
        ) {
          closePasswordModal();
        }

        if (notifDropdown && !notifDropdown.hidden) {
          notifDropdown.hidden = true;
        }

        if (
            notifPrefsModal &&
            !notifPrefsModal.hidden
        ) {
          closeNotifPrefsModal();
        }

      }
  );

  // ============================================================
  // KAN-259: small public API so the Tasks page reuses the same
  // data, edit modal and actions instead of duplicating them.
  // ============================================================
  window.AalSikke = {
    getTasks: () => allTasks,
    getConflicts: () => conflictPartnersByTaskId,
    getTasksState: () => tasksState,
    reloadTasks: loadTasks,
    openEditModal,
    deleteTask,
    apiFetch,
    API_BASE
  };



  // ============================================================
  // INITIALIZATION
  // ============================================================

  updateCurrentDate();
  loadProfile();
  loadTasks();// renders the loading skeletons first, then the numbers
  loadDueReminders();
});
