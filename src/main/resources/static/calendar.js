// Aal Sikke — Calendar
//   S3-11: day and week views
//   S3-12: time-blocking (schedule a task into a time slot)
//   KAN-315: Render START_ONLY tasks as markers instead of blocks
//
// Data:
//   GET /api/schedule-items/range?from=&to=   items overlapping the visible days (S3-02)
//   GET /api/schedule-conflicts/me            overlapping pairs, used for conflict badges (S3-07)
//   GET /api/schedule-items/me                all items, to list unscheduled tasks (S3-12)
//   PUT /api/schedule-items/{id}              partial update: only startTime + endTime are sent,
//                                             so the deadline and everything else stay unchanged
//
// The backend sends every date-time in UTC (ending in Z). `new Date(iso)` turns it
// into the user's local time, and every calculation below works in local time.
//
// Open the schedule form for one task from another page with: calendar.html?schedule=<itemId>

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token';

// Same grouping as the dashboard (dashboard.js roleForTask)
const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY']);
const WORK_TYPES = new Set(['SHIFT']);

const HOUR_HEIGHT = 48;       // px per hour; must match --cal-hour in calendar.css
const MIN_EVENT_HEIGHT = 22;  // px, so very short items stay readable
const DEFAULT_SCROLL_HOUR = 7;
const SLOT_MINUTES = 30;      // clicking an empty slot snaps to the half hour
const DEFAULT_BLOCK_MINUTES = 60;
const MARKER_MINUTES = 30;
const startOnlyInput = document.getElementById('schedule-start-only');
const endField = document.getElementById('schedule-end-field');// layout slot for START_ONLY markers (they have no end time)

document.addEventListener('DOMContentLoaded', () => {

    // ============================================================
    // AUTHENTICATION (same pattern as dashboard.js)
    // ============================================================

    if (!localStorage.getItem(TOKEN_KEY)) {
        window.location.href = 'login.html';
        return;
    }

    function redirectToLogin() {
        localStorage.removeItem(TOKEN_KEY);
        window.location.href = 'login.html';
    }

    async function apiRequest(path, options = {}) {
        const response = await fetch(API_BASE + path, {
            ...options,
            headers: {
                Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}`,
                ...(options.headers || {})
            }
        });
        if (response.status === 401) {
            redirectToLogin();
            const expired = new Error('Your session has expired.');
            expired.sessionExpired = true;
            throw expired;
        }
        return response;
    }

    async function apiFetch(path) {
        const response = await apiRequest(path);
        if (!response.ok) {
            throw new Error(`Server responded ${response.status}`);
        }
        return response.json();
    }

    async function readJsonSafely(response) {
        try {
            return await response.json();
        } catch (_) {
            return null;
        }
    }

    // The backend returns { field, message } or a list of them (see README "Error responses")
    function errorMessageFrom(body, fallback) {
        if (Array.isArray(body) && body.length) return body.map(e => e.message).filter(Boolean).join(' ');
        if (body && body.message) return body.message;
        return fallback;
    }

    document.getElementById('logout-link').addEventListener('click', () => {
        localStorage.removeItem(TOKEN_KEY);
    });
// // ============================================================
//     // THEME (KAN-239)
//     // ============================================================
//
//     const THEME_KEY = 'aalsikke_theme';
//
//     function applyTheme(theme) {
//         document.documentElement.setAttribute('data-theme', theme);
//     }
//
//     const savedTheme = localStorage.getItem(THEME_KEY) || 'light';
//     applyTheme(savedTheme);
//
//     const themeToggle = document.getElementById('theme-toggle');
//     if (themeToggle) {
//         themeToggle.checked = savedTheme === 'dark';
//         themeToggle.addEventListener('change', () => {
//             const newTheme = themeToggle.checked ? 'dark' : 'light';
//             localStorage.setItem(THEME_KEY, newTheme);
//             applyTheme(newTheme);
//         });
//     }
    // ============================================================
    // DOM + STATE
    // ============================================================

    const grid = document.getElementById('cal-grid');
    const statusEl = document.getElementById('cal-status');
    const titleEl = document.getElementById('cal-title');
    const viewButtons = document.querySelectorAll('.cal-view-toggle [data-view]');

    const state = {
        // Phones start on the day view: seven columns do not fit a narrow screen.
        view: window.matchMedia('(max-width: 40rem)').matches ? 'day' : 'week',
        anchor: startOfDay(new Date()),
        requestId: 0,
        scrollPending: true,
        flash: null,        // { message, isError } shown after the next load (e.g. "Scheduled …")
        itemsById: new Map()
    };

    // ============================================================
    // DATE HELPERS (local time)
    // ============================================================

    function startOfDay(date) {
        return new Date(date.getFullYear(), date.getMonth(), date.getDate());
    }

    function addDays(date, days) {
        return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
    }

    // Monday-based weeks
    function startOfWeek(date) {
        const mondayOffset = (date.getDay() + 6) % 7;
        return addDays(startOfDay(date), -mondayOffset);
    }

    function sameDay(a, b) {
        return a.getFullYear() === b.getFullYear()
            && a.getMonth() === b.getMonth()
            && a.getDate() === b.getDate();
    }

    function visibleDays() {
        if (state.view === 'day') {
            return [state.anchor];
        }
        const monday = startOfWeek(state.anchor);
        return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
    }

    function formatTime(date) {
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    }

    function formatShortDate(date) {
        return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
    }

    function formatTitle(days) {
        const first = days[0];
        const last = days[days.length - 1];
        if (days.length === 1) {
            return first.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
        }
        const left = first.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
        const right = last.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        return `${left} – ${right}`;
    }

    const pad = n => String(n).padStart(2, '0');

    // Values for <input type="date"> and <input type="time"> (local time)
    function toDateInput(date) {
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    function toTimeInput(date) {
        return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    // "2026-09-23" + "13:30" -> local Date
    function fromInputs(dateValue, timeValue) {
        const [y, m, d] = dateValue.split('-').map(Number);
        const [hh, mm] = timeValue.split(':').map(Number);
        return new Date(y, m - 1, d, hh, mm);
    }

    // ============================================================
    // ITEM HELPERS
    // ============================================================

    function roleOf(item) {
        const type = String(item.itemType || '').toUpperCase();
        if (STUDENT_TYPES.has(type)) return 'student';
        if (WORK_TYPES.has(type)) return 'work';
        return 'personal';
    }

    // KAN-292/293: priority drives the stripe color (css: .prio-low/.prio-medium/.prio-high/.prio-urgent).
    // Missing/unknown = MEDIUM, the backend default.
    function priorityOf(item) {
        const p = String(item.priority || 'MEDIUM').toLowerCase();
        return ['low', 'medium', 'high', 'urgent'].includes(p) ? p : 'medium';
    }

    function roleLabel(role) {
        return { student: 'Student', work: 'Work', personal: 'Personal' }[role];
    }

    function hasTimeSlot(item) {
        return Boolean(item.startTime && item.endTime);
    }
    function isStartOnlyTask(item) {
        return Boolean(item.startTime && !item.endTime);
    }

    // id -> titles of the items it clashes with
    function buildConflictMap(pairs) {
        const map = new Map();
        const add = (id, otherTitle) => {
            if (!map.has(id)) map.set(id, []);
            map.get(id).push(otherTitle);
        };
        for (const pair of pairs) {
            add(pair.itemOne.id, pair.itemTwo.title);
            add(pair.itemTwo.id, pair.itemOne.title);
        }
        return map;
    }

    /*
     * Side-by-side layout for overlapping blocks in one day column.
     * Blocks are grouped into clusters of transitively overlapping blocks; inside a
     * cluster each block gets the first free lane, and every block in the cluster is
     * as wide as 1 / (lanes used by that cluster).
     */
    function layoutLanes(segments) {
        const sorted = [...segments].sort((a, b) => a.start - b.start || b.end - a.end);
        let cluster = [];
        let laneEnds = [];
        let clusterEnd = -Infinity;

        const closeCluster = () => {
            for (const seg of cluster) seg.lanes = laneEnds.length;
            cluster = [];
            laneEnds = [];
        };

        for (const seg of sorted) {
            if (seg.start >= clusterEnd) {
                closeCluster();
                clusterEnd = -Infinity;
            }
            let lane = laneEnds.findIndex(end => end <= seg.start);
            if (lane === -1) {
                lane = laneEnds.length;
                laneEnds.push(seg.end);
            } else {
                laneEnds[lane] = seg.end;
            }
            seg.lane = lane;
            cluster.push(seg);
            clusterEnd = Math.max(clusterEnd, seg.end);
        }
        closeCluster();
        return sorted;
    }

    // ============================================================
    // RENDERING (DOM + textContent only: titles are user input)
    // ============================================================

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function minutesSince(dayStart, date) {
        return (date - dayStart) / 60000;
    }

    // Enter/Space on a focused block or marker behaves like a click
    function makeActivatable(node, onActivate) {
        node.tabIndex = 0;
        node.setAttribute('role', 'button');
        node.addEventListener('click', event => {
            event.stopPropagation(); // don't also trigger the empty-slot click on the column
            onActivate();
        });
        node.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onActivate();
            }
        });
    }

    function render(days, items, conflictMap) {
        const today = startOfDay(new Date());
        // Remember the scroll position so ‹ / › keep the same hours in view
        const previousScrollTop = grid.querySelector('.cal-scroll')?.scrollTop ?? 0;
        grid.replaceChildren();
        grid.style.setProperty('--cal-days', days.length);
        grid.classList.toggle('cal-grid--day', days.length === 1);

        state.itemsById = new Map(items.map(item => [item.id, item]));

        const inner = el('div', 'cal-inner');

        // --- Header row: weekday + date ---
        const head = el('div', 'cal-row cal-head');
        head.append(el('div', 'cal-corner'));
        for (const day of days) {
            const cell = el('div', 'cal-day-head');
            if (sameDay(day, today)) cell.classList.add('is-today');
            cell.append(
                el('span', 'cal-dow', day.toLocaleDateString('en-US', { weekday: 'short' })),
                el('span', 'cal-date', String(day.getDate()))
            );
            head.append(cell);
        }

        // --- Deadline row: a marker for every deadline that falls on a visible day ---
        const deadlines = el('div', 'cal-row cal-deadlines');
        deadlines.append(el('div', 'cal-gutter-label', 'Due'));
        const deadlineCells = days.map(() => {
            const cell = el('div', 'cal-deadline-cell');
            deadlines.append(cell);
            return cell;
        });

        const withDeadline = items
            .filter(item => item.deadline)
            .map(item => ({ item, due: new Date(item.deadline) }))
            .sort((a, b) => a.due - b.due);

        for (const { item, due } of withDeadline) {
            const index = days.findIndex(day => sameDay(day, due));
            if (index === -1) continue;
            const role = roleOf(item);
            const marker = el('div', `cal-deadline-marker cal-${role} prio-${priorityOf(item)}`);
            if (item.status === 'COMPLETED') marker.classList.add('is-completed');
            if (!item.startTime) marker.classList.add('is-unscheduled');
            marker.append(el('span', 'cal-deadline-text', `${formatTime(due)} ${item.title}`));
            const label = `Due ${formatTime(due)}: ${item.title} (${roleLabel(role)}, ${priorityOf(item)} priority)`
                + (item.startTime ? '. Click to reschedule' : ' — not scheduled yet. Click to schedule');
            marker.title = label;
            marker.setAttribute('aria-label', label);
            makeActivatable(marker, () => openScheduleForm({ item }));
            deadlineCells[index].append(marker);
        }

        // --- Time grid ---
        const scroll = el('div', 'cal-scroll');
        const body = el('div', 'cal-row cal-body');

        const times = el('div', 'cal-times');
        for (let hour = 1; hour < 24; hour++) {
            const label = el('span', 'cal-time-label', `${String(hour).padStart(2, '0')}:00`);
            label.style.top = `${hour * HOUR_HEIGHT}px`;
            times.append(label);
        }
        body.append(times);

        const timed = items.filter(item => item.startTime).map(item => {
            const start = new Date(item.startTime);
            // START_ONLY markers have no end time: give them a short slot so they lay out like a block
            const end = item.endTime ? new Date(item.endTime) : new Date(start.getTime() + MARKER_MINUTES * 60000);
            return { item, start, end };
        });

        let earliestMinute = Infinity;

        days.forEach(day => {
            const dayStart = day;
            const dayEnd = addDays(day, 1);
            const column = el('div', 'cal-day-col');
            if (sameDay(day, today)) column.classList.add('is-today');
            column.title = 'Click an empty slot to schedule a task here';

            // S3-12: click an empty slot -> schedule form for that day and time (snapped to the half hour)
            column.addEventListener('click', event => {
                if (event.target !== column) return; // clicks on blocks are handled by the block
                const minutes = Math.floor((event.offsetY / HOUR_HEIGHT) * 60 / SLOT_MINUTES) * SLOT_MINUTES;
                const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, Math.max(0, minutes));
                openScheduleForm({ start });
            });

            // Clip each item to this day (an item from 23:00 to 01:00 shows in both days)
            const segments = timed
                .filter(t => isStartOnlyTask(t.item)
                    ? t.start >= dayStart && t.start < dayEnd      // a marker shows only on its own day
                    : t.start < dayEnd && t.end > dayStart)
                .map(t => ({
                    item: t.item,
                    realStart: t.start,
                    realEnd: t.end,
                    start: minutesSince(dayStart, t.start > dayStart ? t.start : dayStart),
                    end: minutesSince(dayStart, t.end < dayEnd ? t.end : dayEnd)
                }));

            for (const seg of layoutLanes(segments)) {
                earliestMinute = Math.min(earliestMinute, seg.start);
                column.append(buildEvent(seg, conflictMap));
            }

            // Current-time line on today's column
            if (sameDay(day, today)) {
                const now = el('div', 'cal-now');
                now.style.top = `${minutesSince(dayStart, new Date()) / 60 * HOUR_HEIGHT}px`;
                now.setAttribute('aria-hidden', 'true');
                column.append(now);
            }

            body.append(column);
        });

        scroll.append(body);
        inner.append(head, deadlines, scroll);
        grid.append(inner);

        // The body's vertical scrollbar takes width; give the header rows the same space so columns line up
        const scrollbarWidth = scroll.offsetWidth - scroll.clientWidth;
        grid.style.setProperty('--cal-scrollbar', `${scrollbarWidth}px`);

        // First load, Today and Day/Week switch: show the first item (one hour before it),
        // or 07:00 when there is nothing. Previous/next keep the hours the user was looking at.
        if (state.scrollPending) {
            const startHour = Number.isFinite(earliestMinute)
                ? Math.floor(earliestMinute / 60) - 1
                : DEFAULT_SCROLL_HOUR;
            scroll.scrollTop = Math.max(0, startHour) * HOUR_HEIGHT;
            state.scrollPending = false;
        } else {
            scroll.scrollTop = previousScrollTop;
        }
    }

    /**
     * KAN-315: Render task as either a point marker (START_ONLY) or standard duration block
     */
    function buildEvent(seg, conflictMap) {
        const { item } = seg;
        const role = roleOf(item);

        if (isStartOnlyTask(item)) {
            const marker = el('div', `calendar-event-marker cal-${role} prio-${priorityOf(item)}`);
            marker.style.top = `${(seg.start / 60) * HOUR_HEIGHT}px`;
            marker.style.left = `calc(${(seg.lane / seg.lanes) * 100}% + 2px)`;
            marker.style.width = `calc(${100 / seg.lanes}% - 4px)`;
            marker.append(
                el('span', 'marker-dot'),
                el('span', 'marker-time', formatTime(seg.realStart)),
                el('span', 'marker-title', item.title)
            );

            let description = `${item.title} at ${formatTime(seg.realStart)}, ${roleLabel(role)}, ${priorityOf(item)} priority (start time marker)`;
            const markerClashes = item.occurrence ? null : conflictMap.get(item.id);
            if (markerClashes && markerClashes.length) {
                marker.classList.add('is-conflict');
                description += `. Conflicts with: ${markerClashes.join(', ')}`;
            }
            if (item.status === 'COMPLETED') {
                marker.classList.add('is-completed');
                description += '. Completed';
            }
            if (item.occurrence) {
                marker.classList.add('is-repeat');
                description += '. Weekly repeat';
                makeActivatable(marker, () => setStatus(
                    `"${item.title}" repeats weekly. To change the time of the whole series, click the original (its first date).`));
            } else {
                description += '. Click to reschedule';
                makeActivatable(marker, () => openScheduleForm({ item }));
            }
            marker.title = description;
            marker.setAttribute('aria-label', description);
            return marker;
        }

        // --- Standard duration block rendering ---
        const block = el('div', `cal-event cal-${role} prio-${priorityOf(item)}`);

        const top = seg.start / 60 * HOUR_HEIGHT;
        const height = Math.max((seg.end - seg.start) / 60 * HOUR_HEIGHT, MIN_EVENT_HEIGHT);
        block.style.top = `${top}px`;
        block.style.height = `${height}px`;
        block.style.left = `calc(${(seg.lane / seg.lanes) * 100}% + 2px)`;
        block.style.width = `calc(${100 / seg.lanes}% - 4px)`;

        // KAN-248: "↻" marks items that repeat weekly (the original and its generated repeats)
        const repeats = item.recurring || item.occurrence;
        const titleText = repeats ? `↻ ${item.title}` : item.title;
        const timeText = `${formatTime(seg.realStart)}–${formatTime(seg.realEnd)}`;
        block.append(el('span', 'cal-event-title', titleText), el('span', 'cal-event-time', timeText));

        let description = `${item.title}, ${timeText}, ${roleLabel(role)}, ${priorityOf(item)} priority`;

        // Conflicts are computed for the real item only, so a repeat never shows the original's badge
        const clashes = item.occurrence ? null : conflictMap.get(item.id);
        if (clashes && clashes.length) {
            block.classList.add('is-conflict');
            const badge = el('span', 'cal-conflict-badge', '!');
            badge.setAttribute('aria-hidden', 'true');
            block.append(badge);
            description += `. Conflicts with: ${clashes.join(', ')}`;
        }
        if (item.status === 'COMPLETED') {
            block.classList.add('is-completed');
            description += '. Completed';
        }

        if (item.occurrence) {
            // A generated repeat is read-only: the series is changed through the original item
            block.classList.add('is-repeat');
            description += '. Weekly repeat';
            makeActivatable(block, () => setStatus(
                `"${item.title}" repeats weekly. To change the time of the whole series, click the original (its first date).`));
        } else {
            if (item.recurring) description += '. Repeats weekly';
            description += '. Click to reschedule';
            makeActivatable(block, () => openScheduleForm({ item }));
        }

        block.title = description;
        block.setAttribute('aria-label', description);
        return block;
    }

    // ============================================================
    // KAN-249: WEEKLY REVIEW CARD (week view only)
    // Data: GET /api/daily-reviews/weekly?start=YYYY-MM-DD&end=YYYY-MM-DD (KAN-248)
    // ============================================================

    const summaryEl = document.getElementById('cal-week-summary');

    function statTile(className, value, label) {
        const tile = el('div', `cal-stat ${className}`);
        tile.append(el('span', 'cal-stat-value', String(value)), el('span', 'cal-stat-label', label));
        return tile;
    }

    function renderWeekSummary(data) {
        summaryEl.replaceChildren();

        const head = el('div', 'cal-summary-head');
        head.append(el('h2', 'cal-summary-title', 'Weekly review'));

        const due = data.completedTasks + data.missedTasks;
        const rateText = due === 0
            ? 'Nothing was due yet this week.'
            : `${data.completedTasks} of ${due} due tasks done (${Math.round(data.completedTasks / due * 100)}%).`;
        head.append(el('p', 'cal-summary-rate', rateText));

        const stats = el('div', 'cal-stats');
        stats.append(
            statTile('is-completed', data.completedTasks, 'Completed'),
            statTile('is-missed', data.missedTasks, 'Missed'),
            statTile('is-upcoming', data.upcomingTasks, 'Upcoming')
        );

        // Completed vs missed bar (only when something was due)
        const bar = el('div', 'cal-summary-bar');
        bar.setAttribute('aria-hidden', 'true');
        if (due > 0) {
            const done = el('span', 'cal-bar-completed');
            done.style.width = `${data.completedTasks / due * 100}%`;
            const missed = el('span', 'cal-bar-missed');
            missed.style.width = `${data.missedTasks / due * 100}%`;
            bar.append(done, missed);
        } else {
            bar.hidden = true;
        }

        const reviewText = data.daysReviewed === 0
            ? 'No daily reviews this week.'
            : `Productivity ${data.averageProductivityScore ?? '—'} / 5 across ${data.daysReviewed} reviewed day${data.daysReviewed === 1 ? '' : 's'}.`;

        summaryEl.append(head, stats, bar, el('p', 'cal-summary-reviews', reviewText));

        // KAN-341: busiest day (left out when nothing happens this week)
        if (data.busiestDay) {
            const [y, m, d] = data.busiestDay.split('-').map(Number);
            const label = new Date(y, m - 1, d).toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' });
            const count = `${data.busiestDayCount} task${data.busiestDayCount === 1 ? '' : 's'}`;
            summaryEl.append(el('p', 'cal-summary-reviews cal-summary-busiest', `Busiest day: ${label} · ${count}`));
        }
        summaryEl.hidden = false;
    }

    async function loadWeekSummary(days, requestId) {
        if (state.view !== 'week') {
            summaryEl.hidden = true;
            return;
        }
        try {
            const start = toDateInput(days[0]);
            const end = toDateInput(days[days.length - 1]);
            const data = await apiFetch(`/api/daily-reviews/weekly?start=${start}&end=${end}`);
            if (requestId !== state.requestId) return; // user already moved to another week
            renderWeekSummary(data);
        } catch (error) {
            // The card is extra information: if it fails, hide it and keep the calendar working
            if (!error.sessionExpired) console.error(error);
            summaryEl.hidden = true;
        }
    }


    // ============================================================
    // LOADING
    // ============================================================

    function setStatus(message, isError = false) {
        statusEl.textContent = message;
        statusEl.classList.toggle('is-error', isError);
    }

    function updateToolbar(days) {
        titleEl.textContent = formatTitle(days);
        viewButtons.forEach(button => {
            const active = button.dataset.view === state.view;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
        });
    }

    async function load() {
        const requestId = ++state.requestId;
        const days = visibleDays();
        const from = days[0];
        const to = addDays(days[days.length - 1], 1);

        updateToolbar(days);
        setStatus('Loading your calendar…');
        grid.setAttribute('aria-busy', 'true');

        try {
            const query = `from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`;
            const [items, conflicts] = await Promise.all([
                apiFetch(`/api/schedule-items/range?${query}`),
                apiFetch('/api/schedule-conflicts/me')
            ]);

            // Ignore an older response if the user already clicked next/previous again
            if (requestId !== state.requestId) return;

            render(days, items, buildConflictMap(conflicts));
            loadWeekSummary(days, requestId);
            if (state.flash) {
                setStatus(state.flash.message, state.flash.isError);
                state.flash = null;
            } else if (items.length === 0) {
                setStatus(state.view === 'day' ? 'Nothing scheduled for this day.' : 'Nothing scheduled this week.');
            } else {
                setStatus('');
            }
        } catch (error) {
            if (error.sessionExpired || requestId !== state.requestId) return;
            console.error(error);
            render(days, [], new Map());
            setStatus('Could not load your calendar. Please try again.', true);
        } finally {
            if (requestId === state.requestId) grid.removeAttribute('aria-busy');
        }
    }

    // ============================================================
    // S3-12: SCHEDULE FORM (time-blocking)
    // ============================================================

    const modal = document.getElementById('schedule-modal');
    const form = document.getElementById('schedule-form');
    const modalTitle = document.getElementById('schedule-modal-title');
    const taskField = document.getElementById('schedule-task-field');
    const taskSelect = document.getElementById('schedule-task');
    const taskName = document.getElementById('schedule-task-name');
    const dateInput = document.getElementById('schedule-date');
    const startInput = document.getElementById('schedule-start');
    const endInput = document.getElementById('schedule-end');
    const modalMessage = document.getElementById('schedule-message');
    const saveButton = document.getElementById('schedule-save');

    let fixedItem = null;          // the task being scheduled when opened from a block/marker
    let returnFocusTo = null;

    function setModalMessage(message, type = '') {
        modalMessage.textContent = message;
        modalMessage.className = `modal-message${type ? ` is-${type}` : ''}`;
    }

    function nextFullHour() {
        const now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours() + 1, 0);
    }

    function fillTimes(start, end) {
        dateInput.value = toDateInput(start);
        startInput.value = toTimeInput(start);
        endInput.value = sameDay(end, start) ? toTimeInput(end) : '23:59';
    }

    function syncEndField() {
        endField.hidden = startOnlyInput.checked;
    }
    startOnlyInput.addEventListener('change', syncEndField);

    async function loadUnscheduledTasks() {
        const all = await apiFetch('/api/schedule-items/me');
        return all
            .filter(item => !item.startTime && item.status !== 'COMPLETED')
            .sort((a, b) => new Date(a.deadline) - new Date(b.deadline));
    }

    /*
     * open({ item })  -> schedule / reschedule that task (from a block, a marker or ?schedule=id)
     * open({ start }) -> empty slot or "+ Schedule": pick one of the unscheduled tasks
     */
    async function openScheduleForm({ item = null, start = null } = {}) {
        returnFocusTo = document.activeElement;
        fixedItem = item;
        modal.dataset.itemId = item ? String(item.id) : '';   // KAN-343: "Suggest a time" leaves this task out
        setModalMessage('');
        saveButton.disabled = false;
        startOnlyInput.checked = Boolean(item && isStartOnlyTask(item));
        syncEndField();

        if (item) {
            const scheduled = Boolean(item.startTime);
            modalTitle.textContent = scheduled ? 'Reschedule task' : 'Schedule task';
            taskField.hidden = true;
            taskName.hidden = false;
            taskName.textContent = item.deadline
                ? `${item.title} (due ${formatShortDate(new Date(item.deadline))}, ${formatTime(new Date(item.deadline))})`
                : item.title;
            if (scheduled) {
                const markerStart = new Date(item.startTime);
                fillTimes(markerStart, item.endTime
                    ? new Date(item.endTime)
                    : new Date(markerStart.getTime() + DEFAULT_BLOCK_MINUTES * 60000));
            } else {
                const begin = nextFullHour();
                fillTimes(begin, new Date(begin.getTime() + DEFAULT_BLOCK_MINUTES * 60000));
            }
        } else {
            const begin = start || nextFullHour();
            modalTitle.textContent = 'Schedule a task';
            taskField.hidden = false;
            taskName.hidden = true;
            fillTimes(begin, new Date(begin.getTime() + DEFAULT_BLOCK_MINUTES * 60000));

            taskSelect.replaceChildren(new Option('Loading your tasks…', ''));
            taskSelect.disabled = true;
            saveButton.disabled = true;
        }

        modal.hidden = false;
        (item ? startInput : taskSelect).focus();

        if (!item) {
            try {
                const tasks = await loadUnscheduledTasks();
                taskSelect.replaceChildren();
                if (tasks.length === 0) {
                    taskSelect.append(new Option('No unscheduled tasks', ''));
                    const dashboardLink = el('a', 'modal-link', 'add a task on the dashboard');
                    dashboardLink.href = 'dashboard.html';
                    setModalMessage('', 'info');
                    modalMessage.append(
                        'Every task already has a time slot. To schedule something new, ',
                        dashboardLink,
                        ' (a deadline is enough), or click a task in the calendar to reschedule it.'
                    );
                    return;
                }
                for (const task of tasks) {
                    const due = task.deadline ? ` — due ${formatShortDate(new Date(task.deadline))}` : '';
                    taskSelect.append(new Option(`${task.title}${due}`, task.id));
                }
                taskSelect.disabled = false;
                saveButton.disabled = false;
                taskSelect.focus();
            } catch (error) {
                if (error.sessionExpired) return;
                console.error(error);
                taskSelect.replaceChildren(new Option('Could not load tasks', ''));
                setModalMessage('Could not load your tasks. Please try again.', 'error');
            }
        }
    }

    function closeScheduleForm() {
        modal.hidden = true;
        fixedItem = null;
        if (returnFocusTo && document.contains(returnFocusTo)) returnFocusTo.focus();
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();

        const itemId = fixedItem ? fixedItem.id : taskSelect.value;
        const title = fixedItem ? fixedItem.title : taskSelect.selectedOptions[0]?.textContent;

        if (!itemId) {
            setModalMessage('Choose a task to schedule.', 'error');
            return;
        }
        const startOnly = startOnlyInput.checked;

        if (!dateInput.value || !startInput.value || (!startOnly && !endInput.value)) {
            setModalMessage(startOnly
                ? 'Pick a date and a start time.'
                : 'Pick a date, a start time and an end time.', 'error');
            return;
        }

        const start = fromInputs(dateInput.value, startInput.value);
        const end = startOnly ? null : fromInputs(dateInput.value, endInput.value);

        // Client-side check required by S3-12 (the backend checks it too)
        if (end && end <= start) {
            setModalMessage('The end time must be after the start time.', 'error');
            endInput.focus();
            return;
        }

        saveButton.disabled = true;
        setModalMessage('Saving…', 'info');

        try {
            // Partial update: only the slot and its type are sent, so the deadline and other fields are unchanged
            const payload = startOnly
                ? { startTime: start.toISOString(), timeType: 'START_ONLY' }
                : { startTime: start.toISOString(), endTime: end.toISOString(), timeType: 'TIME_BLOCKED' };
            const response = await apiRequest(`/api/schedule-items/${encodeURIComponent(itemId)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const body = await readJsonSafely(response);

            if (response.status === 403) {
                setModalMessage('You can only schedule your own tasks.', 'error');
                saveButton.disabled = false;
                return;
            }
            if (!response.ok) {
                setModalMessage(errorMessageFrom(body, 'Could not save. Please try again.'), 'error');
                saveButton.disabled = false;
                return;
            }

            // TODO(S3-09): show Yara's shared conflict warning here once it is merged,
            // instead of this plain text. body.conflicts = [{ id, title, startTime, endTime }]
            const conflicts = (body && body.conflicts) || [];
            const slotText = end
                ? `${formatShortDate(start)}, ${formatTime(start)}–${formatTime(end)}`
                : `${formatShortDate(start)} at ${formatTime(start)}`;
            const savedTitle = (body && body.title) || title;
            state.flash = conflicts.length
                ? {
                    message: `Scheduled "${savedTitle}" on ${slotText}. Heads-up: it overlaps with ${conflicts.map(c => `"${c.title}"`).join(', ')}.`,
                    isError: true
                }
                : { message: `Scheduled "${savedTitle}" on ${slotText}.`, isError: false };

            closeScheduleForm();

            // Jump to the day that was scheduled so the block is visible
            state.anchor = startOfDay(start);
            state.scrollPending = true;
            load();
        } catch (error) {
            if (error.sessionExpired) return;
            console.error(error);
            setModalMessage('Could not save. Please check your connection and try again.', 'error');
            saveButton.disabled = false;
        }
    });

    // Keep the end after the start while the user changes the start time
    startInput.addEventListener('change', () => {
        if (startOnlyInput.checked) return;   // End is hidden, leave it alone
        if (!startInput.value || !endInput.value) return;
        const start = fromInputs(dateInput.value || toDateInput(new Date()), startInput.value);
        const end = fromInputs(dateInput.value || toDateInput(new Date()), endInput.value);
        if (end <= start) {
            const newEnd = new Date(start.getTime() + DEFAULT_BLOCK_MINUTES * 60000);
            endInput.value = sameDay(newEnd, start) ? toTimeInput(newEnd) : '23:59';
        }
    });

    document.getElementById('schedule-cancel').addEventListener('click', closeScheduleForm);
    document.getElementById('schedule-close').addEventListener('click', closeScheduleForm);
    modal.addEventListener('click', event => {
        if (event.target === modal) closeScheduleForm(); // click on the dark backdrop
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.hidden) closeScheduleForm();
    });
    document.getElementById('cal-schedule-btn').addEventListener('click', () => openScheduleForm());

    // ============================================================
    // NAVIGATION
    // ============================================================

    document.getElementById('cal-prev').addEventListener('click', () => {
        state.anchor = addDays(state.anchor, state.view === 'week' ? -7 : -1);
        load();
    });

    document.getElementById('cal-next').addEventListener('click', () => {
        state.anchor = addDays(state.anchor, state.view === 'week' ? 7 : 1);
        load();
    });

    document.getElementById('cal-today').addEventListener('click', () => {
        state.anchor = startOfDay(new Date());
        state.scrollPending = true;
        load();
    });

    viewButtons.forEach(button => {
        button.addEventListener('click', () => {
            if (button.dataset.view === state.view) return;
            state.view = button.dataset.view;
            state.scrollPending = true;
            load();
        });
    });

    // calendar.html?schedule=<id> opens the form for that task (e.g. from a dashboard "Schedule" link)
    async function openFromUrl() {
        const params = new URLSearchParams(window.location.search);
        const id = params.get('schedule');
        if (!id) return;
        history.replaceState(null, '', window.location.pathname);
        try {
            const all = await apiFetch('/api/schedule-items/me');
            const item = all.find(i => i.id === id);
            if (item) {
                openScheduleForm({ item });
            } else {
                setStatus('That task was not found.', true);
            }
        } catch (error) {
            if (!error.sessionExpired) console.error(error);
        }
    }

    load();
    openFromUrl();
});