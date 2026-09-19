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

const API_BASE =
    (window.location.protocol === 'http:' ||
        window.location.protocol === 'https:') &&
    window.location.port === '8080'
        ? window.location.origin
        : 'http://localhost:8080';
const API_BASE= 'http://localhost:5500';

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

  const authHeaders = {
    Authorization: `Bearer ${token}`
  };

  function redirectToLogin() {
    localStorage.removeItem(TOKEN_KEY);
    window.location.href = 'login.html';
  }

  async function apiFetch(url, options = {}) {
    const headers = {
      ...authHeaders,
      ...(options.headers || {})
    };

    const response = await fetch(url, {
      ...options,
      headers
    });

    if (response.status === 401) {
      redirectToLogin();
      throw new Error('Your session has expired.');
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

  const avatarBtn = document.getElementById('avatar-btn');
  const profileDropdown = document.getElementById('profile-dropdown');

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
  const profileMode =
      document.getElementById('profile-mode');
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
  let activeFilter = 'all';
  let currentProfile = null;

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
      return 'Hybrid';
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
    if (!task || task.status === 'COMPLETED') {
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
  // LOAD PROFILE
  // ============================================================

  async function loadProfile() {

    try {

      const response = await apiFetch(
          `${API_BASE}/api/auth/me`
      );

      if (!response.ok) {
        throw new Error(
            `Server responded ${response.status}`
        );
      }

      currentProfile =
          await response.json();

      const fullName =
          currentProfile.fullName ||
          currentProfile.username ||
          'User';

      const initials =
          getInitials(fullName);

      // Sidebar
      const profileAvatar =
          document.getElementById('profile-avatar');

      const profileName =
          document.getElementById('profile-name');

      const profileRole =
          document.getElementById('profile-role');

      const greetingText =
          document.getElementById('greeting-text');

      if (profileAvatar) {
        profileAvatar.textContent = initials;
      }

      if (profileName) {
        profileName.textContent = fullName;
      }

      if (profileRole) {
        profileRole.textContent =
            formatProfileMode(
                currentProfile.profileMode
            );
      }

      if (greetingText) {

        const firstName =
            fullName.split(' ')[0];

        greetingText.textContent =
            `Welcome back, ${firstName}`;
      }

      // Top avatar
      if (avatarBtn) {
        avatarBtn.textContent = initials;
      }

      // Task categories
      updateCategoryOptions(
          currentProfile.profileMode
      );

    } catch (error) {

      console.error(
          'Failed to load profile:',
          error
      );
    }
  }

  // ============================================================
  // PROFILE MODAL
  // ============================================================

  function clearProfileMessage() {

    if (!profileMessage) {
      return;
    }

    profileMessage.textContent = '';
    profileMessage.className =
        'modal-message';
  }

  function showProfileMessage(message, isError = false) {

    if (!profileMessage) {
      return;
    }

    profileMessage.textContent = message;

    profileMessage.className =
        isError
            ? 'modal-message error-text'
            : 'modal-message';
  }

  function populateProfileForm() {

    if (!currentProfile) {
      return;
    }

    if (profileFullname) {
      profileFullname.value =
          currentProfile.fullName || '';
    }

    if (profileUsername) {
      profileUsername.value =
          currentProfile.username || '';

      // Username is not accepted by
      // UpdateProfileRequest.
      profileUsername.readOnly = true;
    }

    if (profileEmail) {
      profileEmail.value =
          currentProfile.email || '';
    }

    if (profileMode) {
      profileMode.value =
          currentProfile.profileMode || 'STUDENT';
    }

    if (profileDob) {
      profileDob.value = currentProfile.dateOfBirth || '';
      profileDob.readOnly = true;
    }

    if (profilePhone) {
      profilePhone.value = currentProfile.phoneNumber || '';
    }

    if (modalProfileAvatar) {

      modalProfileAvatar.textContent =
          getInitials(
              currentProfile.fullName ||
              currentProfile.username
          );
    }

    clearProfileMessage();
  }

  function openProfileModal() {

    if (!profileModal) {
      return;
    }

    populateProfileForm();

    profileModal.hidden = false;

    if (profileDropdown) {
      profileDropdown.hidden = true;
    }
  }

  function closeProfileModal() {

    if (!profileModal) {
      return;
    }

    profileModal.hidden = true;
    clearProfileMessage();
  }

  if (editProfileLink) {
    editProfileLink.addEventListener('click', event => {
      event.preventDefault();
      openProfileModal();
    });
  }

  if (sidebarProfileBtn) {
    sidebarProfileBtn.addEventListener(
        'click',
        openProfileModal
    );
  }

  if (closeProfileBtn) {
    closeProfileBtn.addEventListener(
        'click',
        closeProfileModal
    );
  }

  if (cancelProfileBtn) {
    cancelProfileBtn.addEventListener(
        'click',
        closeProfileModal
    );
  }

  // ============================================================
  // SAVE PROFILE
  // ============================================================

  if (profileForm) {

    profileForm.addEventListener(
        'submit',
        async (event) => {

          event.preventDefault();

          const fullName =
              profileFullname?.value.trim() || '';

          const email =
              profileEmail?.value.trim() || '';

          const selectedMode =
              profileMode?.value || '';

          const phoneNumber =
              profilePhone?.value.trim() || '';

          // Frontend validation
          if (!fullName) {
            showProfileMessage(
                'Full name is required.',
                true
            );
            profileFullname?.focus();
            return;
          }

          if (!email) {
            showProfileMessage(
                'Email is required.',
                true
            );
            return;
          }

          const emailPattern =
              /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

          if (!emailPattern.test(email)) {
            showProfileMessage(
                'Please enter a valid email address.',
                true
            );
            return;
          }

          if (
              !['STUDENT', 'WORK', 'HYBRID']
                  .includes(selectedMode)
          ) {
            showProfileMessage(
                'Please select a valid workspace mode.',
                true
            );
            return;
          }

          const payload = {
            fullName,
            email,
            profileMode: selectedMode,
            phoneNumber
          };

          saveProfileBtn.disabled = true;
          saveProfileBtn.textContent =
              'Saving…';

          clearProfileMessage();

          try {

            const response = await apiFetch(
                `${API_BASE}/api/auth/profile`,
                {
                  method: 'PUT',
                  headers: {
                    'Content-Type':
                        'application/json'
                  },
                  body: JSON.stringify(payload)
                }
            );

            if (!response.ok) {

              let errorMessage =
                  `Server responded ${response.status}`;

              try {

                const errorData =
                    await response.json();

                if (errorData.message) {
                  errorMessage =
                      errorData.message;
                } else if (errorData.error) {
                  errorMessage =
                      errorData.error;
                }

              } catch (_) {
                // Response was not JSON.
              }

              showProfileMessage(
                  errorMessage,
                  true
              );

              return;
            }

            const updatedProfile =
                await response.json();

            currentProfile =
                updatedProfile;

            // Refresh visible profile information
            await loadProfile();

            showProfileMessage(
                'Profile updated successfully.'
            );

            setTimeout(() => {
              closeProfileModal();
            }, 800);

          } catch (error) {

            console.error(
                'Failed to update profile:',
                error
            );

            showProfileMessage(
                'Could not update your profile. Please check the backend.',
                true
            );

          } finally {

            saveProfileBtn.disabled = false;
            saveProfileBtn.textContent =
                'Save profile';
          }
        }
    );
  }

  // ============================================================
  // LOAD TASKS
  // ============================================================

  async function loadTasks() {

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

      renderTasks();
      updateSummary();

    } catch (error) {

      console.error(
          'Failed to load tasks:',
          error
      );

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

    let visibleTasks = [...allTasks];

    // Existing dashboard filters
    if (activeFilter === 'today') {

      visibleTasks =
          visibleTasks.filter(task =>
              isToday(getTaskDate(task))
          );

    } else if (activeFilter === 'upcoming') {

      visibleTasks =
          visibleTasks.filter(task =>
              isUpcoming(getTaskDate(task))
          );
    }

    // Sort overdue first, then by date
    visibleTasks.sort((a, b) => {

      const aOverdue =
          isOverdue(a) ? 0 : 1;

      const bOverdue =
          isOverdue(b) ? 0 : 1;

      if (aOverdue !== bOverdue) {
        return aOverdue - bOverdue;
      }

      const aDate =
          new Date(getTaskDate(a) || 0);

      const bDate =
          new Date(getTaskDate(b) || 0);

      return aDate - bDate;
    });

    taskList.innerHTML = '';

    if (emptyState) {
      emptyState.hidden =
          visibleTasks.length !== 0;
    }

    visibleTasks.forEach(task => {

      const li =
          document.createElement('li');

      li.className = 'task-card';
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

    const cards = document.getElementById('dashboard-cards');
    if (cards) {
      const total = allTasks.length;
      const completed = allTasks.filter(task =>
          String(task.status || '').toUpperCase() === 'COMPLETED'
      ).length;
      const pending = allTasks.filter(task =>
          String(task.status || '').toUpperCase() !== 'COMPLETED'
      ).length;

      cards.innerHTML = `
        <div class="dashboard-stat-card"><span>Total tasks</span><strong>${total}</strong></div>
        <div class="dashboard-stat-card"><span>Pending</span><strong>${pending}</strong></div>
        <div class="dashboard-stat-card"><span>Completed</span><strong>${completed}</strong></div>
        <div class="dashboard-stat-card"><span>Overdue</span><strong>${overdue}</strong></div>
      `;
    }
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

            renderTasks();
            updateSummary();

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

            renderTasks();
            updateSummary();

            closeEditModal();

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
      }
  );

  // ============================================================
  // INITIALIZATION
  // ============================================================

  updateCurrentDate();
  loadProfile();
  loadTasks();

});