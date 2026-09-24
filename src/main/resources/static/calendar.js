// Aal Sikke — Calendar (S3-11: day and week views)
//
// Data:
//   GET /api/schedule-items/range?from=&to=   items overlapping the visible days (S3-02)
//   GET /api/schedule-conflicts/me            overlapping pairs, used for conflict badges (S3-07)
//
// The backend sends every date-time in UTC (ending in Z). `new Date(iso)` turns it
// into the user's local time, and every calculation below works in local time.

const API_BASE = window.location.origin;
const TOKEN_KEY = 'aalsikke_token';

// Same grouping as the dashboard (dashboard.js roleForTask)
const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY']);
const WORK_TYPES = new Set(['SHIFT']);

const HOUR_HEIGHT = 48;       // px per hour; must match --cal-hour in calendar.css
const MIN_EVENT_HEIGHT = 22;  // px, so very short items stay readable
const DEFAULT_SCROLL_HOUR = 7;

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

    async function apiFetch(path) {
        const response = await fetch(API_BASE + path, {
            headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` }
        });
        if (response.status === 401) {
            redirectToLogin();
            const expired = new Error('Your session has expired.');
            expired.sessionExpired = true;
            throw expired;
        }
        if (!response.ok) {
            throw new Error(`Server responded ${response.status}`);
        }
        return response.json();
    }

    document.getElementById('logout-link').addEventListener('click', () => {
        localStorage.removeItem(TOKEN_KEY);
    });

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
        scrollPending: true
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

    // ============================================================
    // ITEM HELPERS
    // ============================================================

    function roleOf(item) {
        const type = String(item.itemType || '').toUpperCase();
        if (STUDENT_TYPES.has(type)) return 'student';
        if (WORK_TYPES.has(type)) return 'work';
        return 'personal';
    }

    function roleLabel(role) {
        return { student: 'Student', work: 'Work', personal: 'Personal' }[role];
    }

    function hasTimeSlot(item) {
        return Boolean(item.startTime && item.endTime);
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

    function render(days, items, conflictMap) {
        const today = startOfDay(new Date());
        // Remember the scroll position so ‹ / › keep the same hours in view
        const previousScrollTop = grid.querySelector('.cal-scroll')?.scrollTop ?? 0;
        grid.replaceChildren();
        grid.style.setProperty('--cal-days', days.length);
        grid.classList.toggle('cal-grid--day', days.length === 1);

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
            const marker = el('div', `cal-deadline-marker cal-${role}`);
            if (item.status === 'COMPLETED') marker.classList.add('is-completed');
            marker.append(el('span', 'cal-deadline-text', `${formatTime(due)} ${item.title}`));
            marker.title = `Due ${formatTime(due)}: ${item.title} (${roleLabel(role)})`
                + (hasTimeSlot(item) ? '' : ' — not scheduled yet');
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

        const timed = items.filter(hasTimeSlot).map(item => ({
            item,
            start: new Date(item.startTime),
            end: new Date(item.endTime)
        }));

        let earliestMinute = Infinity;

        days.forEach(day => {
            const dayStart = day;
            const dayEnd = addDays(day, 1);
            const column = el('div', 'cal-day-col');
            if (sameDay(day, today)) column.classList.add('is-today');

            // Clip each item to this day (an item from 23:00 to 01:00 shows in both days)
            const segments = timed
                .filter(t => t.start < dayEnd && t.end > dayStart)
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

    function buildEvent(seg, conflictMap) {
        const { item } = seg;
        const role = roleOf(item);
        const block = el('div', `cal-event cal-${role}`);
        block.tabIndex = 0;

        const top = seg.start / 60 * HOUR_HEIGHT;
        const height = Math.max((seg.end - seg.start) / 60 * HOUR_HEIGHT, MIN_EVENT_HEIGHT);
        block.style.top = `${top}px`;
        block.style.height = `${height}px`;
        block.style.left = `calc(${(seg.lane / seg.lanes) * 100}% + 2px)`;
        block.style.width = `calc(${100 / seg.lanes}% - 4px)`;

        const timeText = `${formatTime(seg.realStart)}–${formatTime(seg.realEnd)}`;
        block.append(el('span', 'cal-event-title', item.title), el('span', 'cal-event-time', timeText));

        const clashes = conflictMap.get(item.id);
        let description = `${item.title}, ${timeText}, ${roleLabel(role)}`;
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

        block.title = description;
        block.setAttribute('aria-label', description);
        return block;
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
            if (items.length === 0) {
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

    load();
});