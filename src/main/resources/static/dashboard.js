// Aal Sikke — Dashboard logic (KAN-36 / KAN-37 / KAN-38)
//
// This talks to the REAL backend endpoint ScheduleItemController exposes:
//   GET    /api/schedule-items/me
//   POST   /api/schedule-items
//   PUT    /api/schedule-items/{id}
//   DELETE /api/schedule-items/{id}
//
// All requests now authenticate via a Bearer JWT stored by login.js, since
// the backend derives "whose data is this" from the token — never from a
// client-supplied user id.

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token';

document.addEventListener('DOMContentLoaded', () => {
  const token = localStorage.getItem(TOKEN_KEY);

  if (!token) {
    window.location.href = 'login.html';
    return;
  }

  const authHeaders = {
    'Authorization': `Bearer ${token}`,
  };

  const taskList = document.getElementById('task-list');
  const emptyState = document.getElementById('empty-state');
  const toggleFormBtn = document.getElementById('toggle-form-btn');
  const cancelTaskBtn = document.getElementById('cancel-task-btn');
  const taskForm = document.getElementById('task-form');
  const saveTaskBtn = document.getElementById('save-task-btn');
  const filterTabs = document.querySelectorAll('.filter-tab');
  const avatarBtn = document.getElementById('avatar-btn');
  const profileDropdown = document.getElementById('profile-dropdown');

  let allTasks = [];
  let activeFilter = 'all';

  // ---- Profile dropdown ----
  avatarBtn.addEventListener('click', () => {
    profileDropdown.hidden = !profileDropdown.hidden;
  });
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.profile-menu')) profileDropdown.hidden = true;
  });

  // ---- Quick capture form show/hide ----
  toggleFormBtn.addEventListener('click', () => {
    taskForm.hidden = !taskForm.hidden;
  });
  cancelTaskBtn.addEventListener('click', () => {
    taskForm.hidden = true;
    taskForm.reset();
  });

  // ---- Filter tabs ----
  filterTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      filterTabs.forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      activeFilter = tab.dataset.filter;
      renderTasks();
    });
  });

  // ---- Fetch tasks from the real backend ----
  async function loadTasks() {
    try {
      const res = await fetch(`${API_BASE}/api/schedule-items/me`, {
        headers: authHeaders,
      });
      if (res.status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        window.location.href = 'login.html';
        return;
      }
      if (!res.ok) throw new Error(`Server responded ${res.status}`);
      allTasks = await res.json();
      renderTasks();
      updateSummary();
    } catch (err) {
      console.error('Failed to load tasks:', err);
      taskList.innerHTML = `<li class="empty-state">Couldn't load tasks. Is the backend running on ${API_BASE}?</li>`;
    }
  }

  function isToday(dateStr) {
    const d = new Date(dateStr);
    const now = new Date();
    return d.toDateString() === now.toDateString();
  }

  function isUpcoming(dateStr) {
    const d = new Date(dateStr);
    const now = new Date();
    return d > now && !isToday(dateStr);
  }

  function priorityClass(priority) {
    if (priority === 'HIGH' || priority === 'URGENT') return 'high';
    if (priority === 'MEDIUM') return 'medium';
    return 'low';
  }

  function formatDue(dateStr) {
    const d = new Date(dateStr);
    return d.toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
    });
  }

  // ---- Render task list ----
  function renderTasks() {
    let visible = allTasks;
    if (activeFilter === 'today') visible = allTasks.filter((t) => isToday(t.startTime));
    if (activeFilter === 'upcoming') visible = allTasks.filter((t) => isUpcoming(t.startTime));

    visible = [...visible].sort((a, b) => {
      const overdueA = new Date(a.startTime) < new Date() ? 0 : 1;
      const overdueB = new Date(b.startTime) < new Date() ? 0 : 1;
      if (overdueA !== overdueB) return overdueA - overdueB;
      return new Date(a.startTime) - new Date(b.startTime);
    });

    taskList.innerHTML = '';
    emptyState.hidden = visible.length !== 0;

    visible.forEach((task) => {
      const li = document.createElement('li');
      li.className = 'task-card';
      li.dataset.id = task.id;
      li.innerHTML = `
        <input type="checkbox" class="task-checkbox" aria-label="Mark ${escapeHtml(task.title)} complete">
        <div class="task-body">
          <p class="task-title">${escapeHtml(task.title)}</p>
          <div class="task-meta">
            <span class="priority-dot ${priorityClass(task.priority)}"></span>
            ${task.itemType ? escapeHtml(task.itemType) + ' &middot; ' : ''}
            Due ${formatDue(task.startTime)}
          </div>
        </div>
        <button class="task-menu-btn" aria-label="Task options">&#8942;</button>
      `;

      li.querySelector('.task-checkbox').addEventListener('change', (e) => {
        li.classList.toggle('is-complete', e.target.checked);
      });

      li.querySelector('.task-menu-btn').addEventListener('click', () => deleteTask(task.id));

      taskList.appendChild(li);
    });
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str ?? '';
    return div.innerHTML;
  }

  // ---- Create task (real POST to backend) ----
  taskForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const title = document.getElementById('task-title').value.trim();
    const due = document.getElementById('task-due').value;
    const priority = document.getElementById('task-priority').value;
    const itemType = document.getElementById('task-category').value;

    if (!title || !due) return;

    const startTime = new Date(due).toISOString();
    const endTime = new Date(new Date(due).getTime() + 30 * 60 * 1000).toISOString();

    // No "user" field needed — the backend assigns ownership from the JWT.
    const payload = {
      title,
      itemType,
      priority,
      startTime,
      endTime,
      isRecurring: false,
    };

    saveTaskBtn.disabled = true;
    saveTaskBtn.textContent = 'Adding…';

    try {
      const res = await fetch(`${API_BASE}/api/schedule-items`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify(payload),
      });
      if (res.status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        window.location.href = 'login.html';
        return;
      }
      if (!res.ok) throw new Error(`Server responded ${res.status}`);
      const created = await res.json();
      allTasks.push(created);
      renderTasks();
      updateSummary();
      taskForm.reset();
      taskForm.hidden = true;
    } catch (err) {
      console.error('Failed to create task:', err);
      alert('Could not save the task — check the console and confirm the backend is running.');
    } finally {
      saveTaskBtn.disabled = false;
      saveTaskBtn.textContent = '+ Add task';
    }
  });

  // ---- Delete task (real DELETE to backend) ----
  async function deleteTask(id) {
    if (!window.confirm('Delete this task?')) return;
    try {
      const res = await fetch(`${API_BASE}/api/schedule-items/${id}`, {
        method: 'DELETE',
        headers: authHeaders,
      });
      if (res.status === 401 || res.status === 403) {
        alert('You do not have permission to delete this task.');
        return;
      }
      if (!res.ok && res.status !== 204) throw new Error(`Server responded ${res.status}`);
      allTasks = allTasks.filter((t) => t.id !== id);
      renderTasks();
      updateSummary();
    } catch (err) {
      console.error('Failed to delete task:', err);
      alert('Could not delete the task — check the console.');
    }
  }


  function updateCategoryOptions(profileMode) {
    const categorySelect = document.getElementById('task-category');
    if (!categorySelect) return;

    const studentOptions = ['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY'];
    const workOptions = ['SHIFT'];
    const sharedOptions = ['PERSONAL'];

    let visibleValues;
    if (profileMode === 'STUDENT') {
      visibleValues = [...studentOptions, ...sharedOptions];
    } else if (profileMode === 'WORK') {
      visibleValues = [...workOptions, ...sharedOptions];
    } else {
      // HYBRID or unknown — show everything
      visibleValues = [...studentOptions, ...workOptions, ...sharedOptions];
    }

    Array.from(categorySelect.options).forEach(option => {
      option.hidden = !visibleValues.includes(option.value);
    });

    // If the currently selected option got hidden, pick the first visible one
    if (categorySelect.selectedOptions[0]?.hidden) {
      const firstVisible = Array.from(categorySelect.options).find(o => !o.hidden);
      if (firstVisible) categorySelect.value = firstVisible.value;
    }
  }

  async function loadProfile() {
    try {
      const res = await fetch(`${API_BASE}/api/auth/me`, {
        headers: authHeaders,
      });
      if (res.status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        window.location.href = 'login.html';
        return;
      }
      if (!res.ok) throw new Error(`Server responded ${res.status}`);
      const profile = await res.json();

      const initials = profile.fullName
          ? profile.fullName.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
          : '??';

      document.getElementById('profile-avatar').textContent = initials;
      document.getElementById('profile-name').textContent = profile.fullName || profile.username;
      document.getElementById('profile-role').textContent =
          profile.profileMode ? profile.profileMode.charAt(0) + profile.profileMode.slice(1).toLowerCase() : '';

      updateCategoryOptions(profile.profileMode);

      document.getElementById('greeting-text').textContent = `Welcome back, ${(profile.fullName || profile.username || '').split(' ')[0]}`;

      const avatarBtn = document.getElementById('avatar-btn');
      if (avatarBtn) avatarBtn.textContent = initials;
    } catch (err) {
      console.error('Failed to load profile:', err);
    }
  }
  function updateSummary() {
    const now = new Date();
    document.getElementById('greeting-date').textContent = now.toLocaleDateString(undefined, {
      weekday: 'long', month: 'short', day: 'numeric',
    });

    const dueToday = allTasks.filter(t => isToday(t.startTime)).length;
    const overdue = allTasks.filter(t => new Date(t.startTime) < now).length;

    const summaryText = `${dueToday} task${dueToday === 1 ? '' : 's'} due today, ${overdue} overdue`;
    document.getElementById('greeting-summary').textContent = summaryText;
    document.getElementById('profile-meta').textContent = summaryText;
  }

  loadProfile();
  loadTasks();
});