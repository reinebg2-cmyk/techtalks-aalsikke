// Aal Sikke — Dashboard logic (KAN-36 / KAN-37 / KAN-38)
//
// This talks to the REAL backend endpoint ScheduleItemController exposes:
//   GET    /api/schedule-items/user/{userId}
//   POST   /api/schedule-items
//   PUT    /api/schedule-items/{id}
//   DELETE /api/schedule-items/{id}
//
// IMPORTANT GAPS TO KNOW ABOUT:
// 1. Auth is now wired up (AuthController: /api/auth/register, /api/auth/login).
//    login.js stores the real logged-in user's id under localStorage key
//    "aalsikke_user_id" on successful login, which is what this page reads
//    below. If that key is missing, the user hasn't logged in — send them
//    to the login page instead of prompting for a raw UUID.
// 2. ScheduleItem has NO "completed"/"status" column in the DB (see
//    V3__create_schedule_items_and_recurrence.sql). So the checkbox below only
//    toggles a CSS class locally — it does NOT persist "done" state to the
//    backend. To make that real, add a `status` or `completed` column via a
//    new Flyway migration + expose it in the model/controller.
// 3. item_type and priority are constrained by DB CHECK constraints
//    (ASSIGNMENT/EXAM/CLASS/STUDY/SHIFT/PERSONAL and LOW/MEDIUM/HIGH/URGENT).
//    The category select below has been matched to these exactly — do not
//    add new option values without also updating the DB migration.

const API_BASE = window.location.origin; // same-origin since served from Spring Boot static/

document.addEventListener('DOMContentLoaded', () => {
  const DEMO_USER_ID_KEY = 'aalsikke_user_id';
  let userId = localStorage.getItem(DEMO_USER_ID_KEY);

  if (!userId) {
    window.location.href = 'login.html';
    return;
  }

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
    if (!userId) return;
    try {
      const res = await fetch(`${API_BASE}/api/schedule-items/user/${userId}`);
      if (!res.ok) throw new Error(`Server responded ${res.status}`);
      allTasks = await res.json();
      renderTasks();
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

    // Overdue/high-priority tasks surface first, matching the "calm but noticeable" spec.
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

      // Local-only "complete" toggle — see gap #2 in the header comment above.
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
    if (!userId) {
      alert('No user id set — reload the page and paste a valid user UUID.');
      return;
    }

    const title = document.getElementById('task-title').value.trim();
    const due = document.getElementById('task-due').value; // "YYYY-MM-DDTHH:mm"
    const priority = document.getElementById('task-priority').value;
    const itemType = document.getElementById('task-category').value;

    if (!title || !due) return;

    // The backend requires both startTime and endTime. The form only asks
    // for a single due date/time, so we default the duration to 30 minutes.
    const startTime = new Date(due).toISOString();
    const endTime = new Date(new Date(due).getTime() + 30 * 60 * 1000).toISOString();

    const payload = {
      user: { id: userId },
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
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error(`Server responded ${res.status}`);
      const created = await res.json();
      allTasks.push(created);
      renderTasks();
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
      const res = await fetch(`${API_BASE}/api/schedule-items/${id}`, { method: 'DELETE' });
      if (!res.ok && res.status !== 204) throw new Error(`Server responded ${res.status}`);
      allTasks = allTasks.filter((t) => t.id !== id);
      renderTasks();
    } catch (err) {
      console.error('Failed to delete task:', err);
      alert('Could not delete the task — check the console.');
    }
  }

  loadTasks();
});
