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
//
// Needs i18n.js loaded first (t, lang, LANGS).

// API_BASE and TOKEN_KEY come from dashboard.js, which calendar.html loads first
// (it also runs the bell and avatar menu in the header). Declaring them again here
// would be a "has already been declared" SyntaxError.

// Same grouping as the dashboard (dashboard.js roleForTask)
const STUDENT_TYPES = new Set(['ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY','PROJECT', 'QUIZ', 'LAB', 'PRESENTATION']);
const WORK_TYPES = new Set(['SHIFT', 'MEETING', 'WORK_TASK', 'PAYMENT_DUE']);

const HOUR_HEIGHT = 48;       // px per hour; must match --cal-hour in calendar.css
const MIN_EVENT_HEIGHT = 22;  // px, so very short items stay readable
const DEFAULT_SCROLL_HOUR = 7;
const SLOT_MINUTES = 30;      // clicking an empty slot snaps to the half hour
const DEFAULT_BLOCK_MINUTES = 60;
const MARKER_MINUTES = 30;
const DUE_ROW_LIMIT = 3;       // KAN-354: deadlines shown per day before "+N more"
const MULTI_DAY_MINUTES = 24 * 60;   // KAN-354: tasks this long or longer go in the Multi-day row
const MAX_LANES_WEEK = 2;     // KAN-354: overlapping tasks side by side in week view, the rest behind "+N more"
const MAX_LANES_DAY = 4;      // KAN-354: same, in day view (the column is much wider)
const startOnlyInput = document.getElementById('schedule-start-only');
const endField = document.getElementById('schedule-end-field');// layout slot for START_ONLY markers (they have no end time)

document.addEventListener('DOMContentLoaded', () => {

    // ============================================================
    // AUTHENTICATION (same pattern as dashboard.js)
    // ============================================================

    if (!getToken()) {
        window.location.href = 'login.html';
        return;
    }

    function redirectToLogin() {
        clearToken();
        window.location.href = 'login.html';
    }

    async function apiRequest(path, options = {}) {
        const response = await fetch(API_BASE + path, {
            ...options,
            headers: {
                Authorization: `Bearer ${getToken()}`,
                ...(options.headers || {})
            }
        });
        if (response.status === 401) {
            redirectToLogin();
            const expired = new Error(t('err.session'));
            expired.sessionExpired = true;
            throw expired;
        }
        return response;
    }

    async function apiFetch(path) {
        const response = await apiRequest(path);
        if (!response.ok) {
            throw new Error(t('err.server', { status: response.status }));
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
        clearToken();
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
        scrollPending: true,
        flash: null,        // { message, isError } shown after the next load (e.g. "Scheduled …")
        itemsById: new Map(),
        conflicts: new Map(),   // KAN-354: id -> titles it clashes with (for the preview card)
        openAfterLoad: null     // KAN-363: id of an original whose card opens after the next load ("Go to original")
    };

    // ============================================================
    // DATE HELPERS (local time)
    // ============================================================

    // The locale of the selected language ("en-GB" or "ar-LB")
    const loc = () => LANGS[lang].locale;
    // "Earlier" and "later" arrows must point the other way in right-to-left text
    const arrowLater = () => (LANGS[lang].dir === 'rtl' ? '←' : '→');
    const arrowEarlier = () => (LANGS[lang].dir === 'rtl' ? '→' : '←');
    // ‹ › are mirrored by the browser itself in RTL text, so no swap here (← → above are not mirrored)
    const arrowPrev = () => '‹';
    const arrowNext = () => '›';

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
        // 12-hour clock ("11:00 PM"), the same as the Tasks page and the dashboard
        return date.toLocaleTimeString(loc(), { hour: 'numeric', minute: '2-digit', hour12: true });
    }

    function formatShortDate(date) {
        return date.toLocaleDateString(loc(), { weekday: 'short', month: 'short', day: 'numeric' });
    }

    function formatTitle(days) {
        const first = days[0];
        const last = days[days.length - 1];
        if (days.length === 1) {
            return first.toLocaleDateString(loc(), { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
        }
        const left = first.toLocaleDateString(loc(), { month: 'short', day: 'numeric' });
        const right = last.toLocaleDateString(loc(), { month: 'short', day: 'numeric', year: 'numeric' });
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
        return t('group.' + role);   // student | work | personal
    }

    // "Medium priority" in the current language
    function prioText(item) {
        return t('board.priorityLabel', { p: t('prio.' + priorityOf(item).toUpperCase()) });
    }

    // Joins the parts of a screen-reader description: "A. B. C"
    function describe(parts) {
        return parts.filter(Boolean).join('. ');
    }

    function namesList(names) {
        return names.join(t('common.listSep'));
    }

    function hasTimeSlot(item) {
        return Boolean(item.startTime && item.endTime);
    }
    function isStartOnlyTask(item) {
        return Boolean(item.startTime && !item.endTime);
    }

    // KAN-354: the Due row is for deadlines only. A task with a time slot whose deadline is
    // simply its end time is already drawn in the grid, so it is not repeated in the Due row.
    function isDueRowItem(item) {
        if (!item.deadline) return false;
        if (!item.startTime || !item.endTime) return true;
        return new Date(item.deadline).getTime() !== new Date(item.endTime).getTime();
    }


    // KAN-354: tasks of 24 hours or more go in the Multi-day row, not the hour grid
    function isMultiDay(item) {
        return hasTimeSlot(item)
            && new Date(item.endTime) - new Date(item.startTime) >= MULTI_DAY_MINUTES * 60000;
    }

    // ---- KAN-354: the small list behind "+N more" in the grid ----
    function closeMoreLists() {
        closePreview();
        document.querySelectorAll('.cal-more-list').forEach(list => list.remove());
        document.querySelectorAll('.cal-more[aria-expanded="true"]')
            .forEach(chip => chip.setAttribute('aria-expanded', 'false'));
    }


    function toggleMoreList(chip, hidden, day, alignRight) {
        const wasOpen = chip.getAttribute('aria-expanded') === 'true';
        closeMoreLists();
        if (wasOpen) return;

        const list = el('div', `cal-more-list${alignRight ? ' is-right' : ''}`);
        list.style.top = `calc(${chip.style.top} + 1.5rem)`;
        list.setAttribute('role', 'dialog');
        list.setAttribute('aria-label', t('cal.moreTasks', { n: hidden.length }));
        list.addEventListener('click', event => event.stopPropagation());

        for (const seg of hidden) {
            const { item } = seg;
            const row = el('button', `cal-more-item cal-${roleOf(item)}`);
            row.type = 'button';
            row.append(
                el('span', 'cal-more-dot'),
                el('span', 'cal-more-title', item.title),
                el('span', 'cal-more-time', `${formatTime(seg.realStart)}–${formatTime(seg.realEnd)}`)
            );
            row.addEventListener('click', () => openPreview(item, chip));
            list.append(row);
        }

        if (state.view !== 'day') {
            const dayLink = el('button', 'cal-more-day', t('cal.openDay'));
            dayLink.type = 'button';
            dayLink.addEventListener('click', () => {
                closeMoreLists();
                state.view = 'day';
                state.anchor = startOfDay(day);
                state.scrollPending = true;
                load();
            });
            list.append(dayLink);
        }

        chip.after(list);
        chip.setAttribute('aria-expanded', 'true');
        list.querySelector('button')?.focus();
    }

    document.addEventListener('click', closeMoreLists);
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') closeMoreLists();
    });
    // KAN-354: a round arrow on the grid header that does the same as the toolbar's ‹ / › button
    function gridArrow(symbol, label, toolbarId, extraClass = '') {
        const button = el('button', `cal-grid-arrow ${extraClass}`.trim(), symbol);
        button.type = 'button';
        button.title = label;
        button.setAttribute('aria-label', label);
        button.addEventListener('click', () => document.getElementById(toolbarId).click());
        return button;
    }

    // KAN-354: preview card when a task is clicked (what, when, category, overlaps) with Reschedule and Mark done.
    // Click the same task again, click outside or press Escape to close it.
    let previewCard = null;
    let previewAnchor = null;

    function closePreview(returnFocus = false) {
        if (!previewCard) return;
        previewCard.remove();
        previewCard = null;
        if (returnFocus && previewAnchor && previewAnchor.isConnected) previewAnchor.focus();
        previewAnchor = null;
    }

    // "Mon, Oct 12, 9:00 AM–11:00 AM" / "Sun, Oct 11 4:00 PM → Tue, Oct 13 11:00 AM" / "Due Mon, Oct 12, 12:00 PM"
    function previewWhen(item) {
        const lines = [];
        if (item.startTime) {
            const start = new Date(item.startTime);
            if (item.endTime) {
                const end = new Date(item.endTime);
                lines.push(sameDay(start, end)
                    ? `${formatShortDate(start)}, ${formatTime(start)}–${formatTime(end)}`
                    : `${formatShortDate(start)} ${formatTime(start)} ${arrowLater()} ${formatShortDate(end)} ${formatTime(end)}`);
            } else {
                lines.push(t('cal.at', { date: formatShortDate(start), time: formatTime(start) }));
            }
        }
        if (isDueRowItem(item)) {
            const due = new Date(item.deadline);
            lines.push(t('cal.dueAt', { date: formatShortDate(due), time: formatTime(due) }));
        }
        return lines;
    }

    function openPreview(item, anchor) {
        const sameTask = previewAnchor === anchor;
        closeMoreLists();   // also closes an open card
        if (sameTask) return;

        const role = roleOf(item);
        const done = item.status === 'COMPLETED';

        const card = el('div', `cal-preview cal-${role}`);
        card.setAttribute('role', 'dialog');
        card.setAttribute('aria-label', item.title);
        card.addEventListener('click', event => event.stopPropagation());

        const head = el('div', 'cal-preview-head');
        const close = el('button', 'cal-preview-close', '✕');
        close.type = 'button';
        close.setAttribute('aria-label', t('common.close'));
        close.addEventListener('click', () => closePreview(true));
        head.append(el('span', 'cal-preview-dot'), el('strong', 'cal-preview-title', item.title), close);
        card.append(head);

        for (const line of previewWhen(item)) card.append(el('p', 'cal-preview-when', line));

        const meta = [roleLabel(role), prioText(item)];
        if (item.recurring || item.occurrence) meta.push(t('repeat.title'));
        if (done) meta.push(t('board.done'));
        card.append(el('p', 'cal-preview-meta', meta.join(' · ')));

        const clashes = item.occurrence ? null : state.conflicts.get(item.id);
        if (clashes && clashes.length) {
            card.append(el('p', 'cal-preview-overlap', t('cal.overlapsWith', { names: namesList(clashes) })));
        }

        const actions = el('div', 'cal-preview-actions');
        if (item.occurrence) {
            // KAN-363: a repeat can be marked done on its own; the series is changed on the original
            card.append(el('p', 'cal-preview-note', t('cal.repeatNote')));
            if (item.seriesStart) {
                const original = el('button', 'btn-secondary', t('cal.goOriginal'));
                original.type = 'button';
                original.addEventListener('click', () => goToOriginal(item));
                actions.append(original);
            }
        } else {
            const edit = el('button', 'btn-secondary', item.startTime ? t('cal.reschedule') : t('cal.scheduleBtn'));
            edit.type = 'button';
            edit.addEventListener('click', () => {
                closePreview(true);
                openScheduleForm({ item });
            });
            actions.append(edit);
        }
        const toggle = el('button', 'btn-primary', done ? t('cal.markNotDone') : t('cal.markDone'));
        toggle.type = 'button';
        toggle.addEventListener('click', () => setDone(item, !done, toggle));
        actions.append(toggle);
        card.append(actions);

        document.body.append(card);
        previewCard = card;
        previewAnchor = anchor;

        // Next to the task (right side, or left if there is no room), kept inside the window
        const box = anchor.getBoundingClientRect();
        const gap = 8;
        let left = box.right + gap;
        if (left + card.offsetWidth > window.innerWidth - gap) left = box.left - card.offsetWidth - gap;
        if (left < gap) left = Math.max(gap, Math.min(box.left, window.innerWidth - card.offsetWidth - gap));
        const top = Math.max(gap, Math.min(box.top, window.innerHeight - card.offsetHeight - gap));
        card.style.left = `${left}px`;
        card.style.top = `${top}px`;
        card.querySelector('.cal-preview-actions button, .cal-preview-close').focus();
    }

    // KAN-363: "Go to original" shows the week (or day) of the series' first date and opens its card there
    function goToOriginal(item) {
        closePreview();
        state.anchor = startOfDay(new Date(item.seriesStart));
        state.scrollPending = false;
        state.openAfterLoad = item.id;
        load();
    }

    function openPendingOriginal(items) {
        const id = state.openAfterLoad;
        state.openAfterLoad = null;
        if (!id) return;
        const original = items.find(i => i.id === id && !i.occurrence);
        const node = original && grid.querySelector(`[data-original-id="${CSS.escape(id)}"]`);
        if (!node) return;
        node.scrollIntoView({ block: 'center' });
        // Open once the scroll has settled: the card closes itself on scroll
        setTimeout(() => openPreview(original, node), 150);
    }

    async function setDone(item, done, button) {
        button.disabled = true;
        try {
            const response = await apiRequest(`/api/schedule-items/${encodeURIComponent(item.id)}/status`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                // KAN-363: a repeat sends its own date, so only that week changes
                body: JSON.stringify(item.occurrence
                    ? { status: done ? 'COMPLETED' : 'PENDING', occurrenceDate: item.occurrenceDate }
                    : { status: done ? 'COMPLETED' : 'PENDING' })
            });
            if (!response.ok) {
                throw new Error(errorMessageFrom(await readJsonSafely(response), t('err.server', { status: response.status })));
            }
            closePreview();
            state.flash = { message: t(done ? 'cal.markedDone' : 'cal.markedNotDone', { title: item.title }), isError: false };
            load();
        } catch (error) {
            if (error.sessionExpired) return;
            console.error(error);
            button.disabled = false;
            setStatus(t('cal.updateFailed', { title: item.title, error: error.message }), true);
        }
    }

    // The card is fixed on screen, so it closes when the page or the calendar scrolls
    window.addEventListener('scroll', () => closePreview(), true);
    window.addEventListener('resize', () => closePreview());
    // KAN-354: "13 tasks · busy 5h" under each date. Busy = time covered by tasks with a time slot
    // (overlaps counted once; multi-day tasks are not counted as busy all day).
    function dayLoadText(day, items) {
        const dayEnd = addDays(day, 1);
        let count = 0;
        const ranges = [];
        for (const item of items) {
            if (item.startTime) {
                const start = new Date(item.startTime);
                const end = item.endTime ? new Date(item.endTime) : new Date(start.getTime() + MARKER_MINUTES * 60000);
                if (!(start < dayEnd && end > day)) continue;
                count++;
                if (item.endTime && !isMultiDay(item)) {
                    ranges.push([Math.max(start, day), Math.min(end, dayEnd)]);
                }
            } else if (isDueRowItem(item) && sameDay(new Date(item.deadline), day)) {
                count++;
            }
        }
        if (count === 0) return '';
        ranges.sort((a, b) => a[0] - b[0]);
        let busy = 0;
        let until = -Infinity;
        for (const [start, end] of ranges) {
            const from = Math.max(start, until);
            if (end > from) busy += end - from;
            until = Math.max(until, end);
        }
        const minutes = Math.round(busy / 900000) * 15;   // nearest 15 minutes
        const h = Math.floor(minutes / 60);
        const m = minutes % 60;
        const busyText = h && m ? t('cal.durHM', { h, m }) : h ? t('cal.durH', { h }) : t('cal.durM', { m });
        const tasks = `${count} ${t(count === 1 ? 'task.one' : 'task.many')}`;
        return minutes > 0 ? `${tasks} · ${t('cal.busy', { time: busyText })}` : tasks;
    }

    // "Sun 4:00 PM"
    function formatDayTime(date) {
        return `${date.toLocaleDateString(loc(), { weekday: 'short' })} ${formatTime(date)}`;
    }

    /*
     * KAN-354: the Multi-day row above the Due row. Each multi-day task is one bar across the
     * visible days it covers ("←" / "→" when it continues outside the week); bars that share a
     * day go on separate lines. Returns null when there is nothing to show (no empty row).
     */
    function buildMultiDayRow(days, items, conflictMap) {
        const firstDay = days[0];
        const afterLastDay = addDays(days[days.length - 1], 1);
        const bars = items
            .filter(isMultiDay)
            .map(item => ({ item, start: new Date(item.startTime), end: new Date(item.endTime) }))
            .filter(bar => bar.start < afterLastDay && bar.end > firstDay)
            .map(bar => {
                const lastMoment = new Date(bar.end.getTime() - 1);
                const from = bar.start < firstDay ? 0 : days.findIndex(day => sameDay(day, bar.start));
                const to = bar.end > afterLastDay ? days.length - 1 : days.findIndex(day => sameDay(day, lastMoment));
                return { ...bar, from, to };
            })
            .sort((a, b) => a.from - b.from || b.to - a.to);
        if (!bars.length) return null;

        const laneEnds = [];
        for (const bar of bars) {
            let lane = laneEnds.findIndex(end => end < bar.from);
            if (lane === -1) {
                lane = laneEnds.length;
                laneEnds.push(bar.to);
            } else {
                laneEnds[lane] = bar.to;
            }
            bar.lane = lane;
        }

        const row = el('div', 'cal-row cal-multiday');
        row.style.gridTemplateRows = `repeat(${laneEnds.length}, auto)`;
        const label = el('div', 'cal-gutter-label', t('cal.multiDay'));
        label.style.gridColumn = '1';
        label.style.gridRow = '1 / -1';
        row.append(label);
        days.forEach((day, index) => {
            const cell = el('div', 'cal-multiday-cell');
            cell.style.gridColumn = String(index + 2);
            cell.style.gridRow = '1 / -1';
            row.append(cell);
        });

        for (const bar of bars) {
            const { item } = bar;
            const role = roleOf(item);
            const node = el('div', `cal-multiday-bar cal-${role} prio-${priorityOf(item)}`);
            node.style.gridColumn = `${bar.from + 2} / ${bar.to + 3}`;
            node.style.gridRow = String(bar.lane + 1);
            const before = bar.start < firstDay ? `${arrowEarlier()} ` : '';
            const after = bar.end > afterLastDay ? ` ${arrowLater()}` : '';
            const when = `${formatDayTime(bar.start)} ${arrowLater()} ${formatDayTime(bar.end)}`;
            node.append(
                el('span', 'cal-multiday-title', `${before}${item.title}${after}`),
                el('span', 'cal-multiday-time', when)
            );

            const parts = [t('cal.blockDesc', { title: item.title, time: when, role: roleLabel(role), prio: prioText(item) })];
            const clashes = item.occurrence ? null : conflictMap.get(item.id);
            if (clashes && clashes.length) parts.push(t('cal.conflictsWith', { names: namesList(clashes) }));
            if (item.status === 'COMPLETED') {
                node.classList.add('is-completed');
                parts.push(t('status.COMPLETED'));
            }
            if (item.occurrence) {
                node.classList.add('is-repeat');
                parts.push(t('cal.weeklyRepeat'));
                makeActivatable(node, () => openPreview(item, node));
            } else {
                parts.push(t('cal.clickDetails'));
                makeActivatable(node, () => openPreview(item, node));
            }
            const description = describe(parts);
            node.title = description;
            node.setAttribute('aria-label', description);
            row.append(node);
        }
        return row;
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
            const group = {
                start: Math.min(...cluster.map(s => s.start)),
                end: Math.max(...cluster.map(s => s.end)),
                size: cluster.length
            };
            for (const seg of cluster) {
                seg.lanes = laneEnds.length;
                seg.group = group;   // KAN-354: used for the "N overlap" outline
            }
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
        const maxLanes = days.length === 1 ? MAX_LANES_DAY : MAX_LANES_WEEK;   // KAN-354

        const inner = el('div', 'cal-inner');

        // --- Header row: weekday + date ---
        const head = el('div', 'cal-row cal-head');
        // KAN-354: previous / next arrows on the grid itself, so moving doesn't need a scroll back up to the toolbar
        const isDay = state.view === 'day';
        const corner = el('div', 'cal-corner');
        corner.append(gridArrow(arrowPrev(), t(isDay ? 'cal.prevDay' : 'cal.prevWeek'), 'cal-prev'));
        head.append(corner, gridArrow(arrowNext(), t(isDay ? 'cal.nextDay' : 'cal.nextWeek'), 'cal-next', 'is-next'));
        for (const day of days) {
            const cell = el('div', 'cal-day-head');
            if (sameDay(day, today)) cell.classList.add('is-today');
            cell.append(
                el('span', 'cal-dow', day.toLocaleDateString(loc(), { weekday: 'short' })),
                el('span', 'cal-date', day.toLocaleDateString(loc(), { day: 'numeric' }))
            );
            const load = dayLoadText(day, items);   // KAN-354
            if (load) cell.append(el('span', 'cal-day-load', load));
            head.append(cell);
        }

        // --- KAN-354: Multi-day row (tasks of 24 hours or more), only when there is one ---
        const multiDayRow = buildMultiDayRow(days, items, conflictMap);

        // --- Deadline row: a marker for every deadline that falls on a visible day ---
        const deadlines = el('div', 'cal-row cal-deadlines');
        deadlines.append(el('div', 'cal-gutter-label', t('task.dueLabel')));
        const deadlineCells = days.map(() => {
            const cell = el('div', 'cal-deadline-cell');
            deadlines.append(cell);
            return cell;
        });

        const withDeadline = items
            .filter(isDueRowItem)
            .map(item => ({ item, due: new Date(item.deadline) }))
            .sort((a, b) => a.due - b.due);

        for (const { item, due } of withDeadline) {
            const index = days.findIndex(day => sameDay(day, due));
            if (index === -1) continue;
            const role = roleOf(item);
            const marker = el('div', `cal-deadline-marker cal-${role} prio-${priorityOf(item)}`);
            if (item.status === 'COMPLETED') marker.classList.add('is-completed');
            if (!item.startTime) marker.classList.add('is-unscheduled');
            marker.append(el('span', 'cal-deadline-text', item.title), el('span', 'cal-deadline-time', formatTime(due)));
            const label = describe([
                t('cal.dueMarker', { time: formatTime(due), title: item.title, role: roleLabel(role), prio: prioText(item) })
                    + (item.startTime ? '' : ` — ${t('cal.notScheduled')}`),
                t('cal.clickDetails')
            ]);
            marker.title = label;
            marker.setAttribute('aria-label', label);
            makeActivatable(marker, () => openPreview(item, marker));
            deadlineCells[index].append(marker);
        }

        // KAN-382: standalone reminders as 🔔 markers in the Due row; clicking one opens the bell
        for (const reminder of (state.reminders || [])) {
            const at = new Date(reminder.remindAt);
            const index = days.findIndex(day => sameDay(day, at));
            if (index === -1) continue;
            const marker = el('div', 'cal-deadline-marker cal-reminder-marker');
            marker.append(
                el('span', 'cal-deadline-text', `🔔 ${reminder.scheduleItemTitle}`),
                el('span', 'cal-deadline-time', formatTime(at))
            );
            const label = t('cal.reminderMarker', { time: formatTime(at), title: reminder.scheduleItemTitle });
            marker.title = label;
            marker.setAttribute('aria-label', label);
            marker.setAttribute('role', 'button');
            marker.tabIndex = 0;
            const openBell = event => {
                event.stopPropagation();   // otherwise the "click outside" handler closes the bell right away
                document.getElementById('notif-btn')?.click();
            };
            marker.addEventListener('click', openBell);
            marker.addEventListener('keydown', event => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    openBell(event);
                }
            });
            deadlineCells[index].append(marker);
        }

        // KAN-354: at most DUE_ROW_LIMIT deadlines per day; the rest behind "+N more" (click again for fewer)
        deadlineCells.forEach(cell => {
            const markers = [...cell.querySelectorAll('.cal-deadline-marker')];
            if (markers.length <= DUE_ROW_LIMIT) return;
            const extra = markers.slice(DUE_ROW_LIMIT);
            extra.forEach(marker => { marker.hidden = true; });
            const toggle = el('button', 'cal-deadline-more', t('cal.moreN', { n: extra.length }));
            toggle.type = 'button';
            toggle.setAttribute('aria-expanded', 'false');
            toggle.addEventListener('click', event => {
                event.stopPropagation();
                const open = toggle.getAttribute('aria-expanded') !== 'true';
                extra.forEach(marker => { marker.hidden = !open; });
                toggle.setAttribute('aria-expanded', String(open));
                toggle.textContent = open ? t('cal.showFewer') : t('cal.moreN', { n: extra.length });
            });
            cell.append(toggle);
        });

        // --- Time grid ---
        const scroll = el('div', 'cal-scroll');
        const body = el('div', 'cal-row cal-body');

        const times = el('div', 'cal-times');
        for (let hour = 1; hour < 24; hour++) {
            const label = el('span', 'cal-time-label', `${hour % 12 === 0 ? 12 : hour % 12} ${hour < 12 ? t('cal.am') : t('cal.pm')}`);
            label.style.top = `${hour * HOUR_HEIGHT}px`;
            times.append(label);
        }
        body.append(times);

        const timed = items.filter(item => item.startTime && !isMultiDay(item)).map(item => {
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
            column.title = t('cal.emptySlot');

            // S3-12: click an empty slot -> schedule form for that day and time (snapped to the half hour)
            column.addEventListener('click', event => {
                if (event.target !== column) return; // clicks on blocks are handled by the block
                const minutes = Math.floor((event.offsetY / HOUR_HEIGHT) * 60 / SLOT_MINUTES) * SLOT_MINUTES;
                const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, Math.max(0, minutes));
                openScheduleForm({ start });
            });

            // Clip each item to this day (an item from 23:00 to 01:00 shows in both days)
            const segments = timed
                .filter(entry => isStartOnlyTask(entry.item)
                    ? entry.start >= dayStart && entry.start < dayEnd      // a marker shows only on its own day
                    : entry.start < dayEnd && entry.end > dayStart)
                .map(entry => ({
                    item: entry.item,
                    realStart: entry.start,
                    realEnd: entry.end,
                    start: minutesSince(dayStart, entry.start > dayStart ? entry.start : dayStart),
                    end: minutesSince(dayStart, entry.end < dayEnd ? entry.end : dayEnd)
                }));

            const laidOut = layoutLanes(segments);

            // KAN-354: at most maxLanes side by side; the rest of a group goes behind "+N more"
            const hiddenByGroup = new Map();
            for (const seg of laidOut) {
                if (seg.lanes <= maxLanes) continue;
                if (seg.lane >= maxLanes - 1) {
                    seg.hidden = true;
                    if (!hiddenByGroup.has(seg.group)) hiddenByGroup.set(seg.group, []);
                    hiddenByGroup.get(seg.group).push(seg);
                } else {
                    seg.lanes = maxLanes;
                }
            }
            const dayIndex = days.indexOf(day);
            hiddenByGroup.forEach(hidden => {
                const chip = el('button', 'cal-more', maxLanes === MAX_LANES_WEEK ? `+${hidden.length}` : t('cal.moreN', { n: hidden.length }));
                chip.setAttribute('aria-label', t('cal.moreTasks', { n: hidden.length }));
                chip.type = 'button';
                chip.style.top = `${hidden[0].start / 60 * HOUR_HEIGHT}px`;
                chip.style.left = `calc(${((maxLanes - 1) / maxLanes) * 100}% + 2px)`;
                chip.style.width = `calc(${100 / maxLanes}% - 4px)`;
                chip.setAttribute('aria-expanded', 'false');
                chip.title = hidden.map(seg => `${formatTime(seg.realStart)} ${seg.item.title}`).join('\n');
                chip.addEventListener('click', event => {
                    event.stopPropagation();
                    toggleMoreList(chip, hidden, day, dayIndex >= days.length - 2);
                });
                column.append(chip);
            });

            // KAN-354: one calm outline with an "N overlap" label per group of overlapping tasks
            new Set(laidOut.map(seg => seg.group)).forEach(group => {
                if (group.size < 2) return;
                const outline = el('div', 'cal-overlap');
                outline.style.top = `${group.start / 60 * HOUR_HEIGHT}px`;
                outline.style.height = `${Math.max((group.end - group.start) / 60 * HOUR_HEIGHT, MIN_EVENT_HEIGHT)}px`;
                outline.append(el('span', 'cal-overlap-label', t('cal.overlap', { n: group.size })));
                if (group.end > 24 * 60 - 15) outline.classList.add('is-at-end');   // label would be cut off below 24:00
                column.append(outline);
            });

            for (const seg of laidOut) {
                earliestMinute = Math.min(earliestMinute, seg.start);
                if (!seg.hidden) column.append(buildEvent(seg, conflictMap));   // KAN-354: hidden ones are behind "+N more"
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
        inner.append(head);
        if (multiDayRow) inner.append(multiDayRow);   // KAN-354
        inner.append(deadlines, scroll);
        grid.append(inner);

        // The body's vertical scrollbar takes width; give the header rows the same space so columns line up
        const scrollbarWidth = scroll.offsetWidth - scroll.clientWidth;
        grid.style.setProperty('--cal-scrollbar', `${scrollbarWidth}px`);

        // First load, Today and Day/Week switch: show the first item (one hour before it),
        // or 07:00 when there is nothing. Previous/next keep the hours the user was looking at.
        if (state.scrollPending) {
            // KAN-354: start at 07:00 (earlier if a task starts before then); only jump down when
            // the first task would not be visible from 07:00 at all, e.g. an evening-only week
            const viewHours = scroll.clientHeight / HOUR_HEIGHT;
            let startHour = DEFAULT_SCROLL_HOUR;
            if (Number.isFinite(earliestMinute)) {
                const firstHour = Math.floor(earliestMinute / 60) - 1;
                const firstVisibleFrom7 = earliestMinute / 60 < DEFAULT_SCROLL_HOUR + viewHours - 1;
                startHour = firstVisibleFrom7 ? Math.min(firstHour, DEFAULT_SCROLL_HOUR) : firstHour;
            }
            scroll.scrollTop = Math.max(0, startHour * HOUR_HEIGHT - 12);   // KAN-354: a little above the hour, so its label isn't cut in half
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

            const parts = [t('cal.markerDesc', {
                title: item.title, time: formatTime(seg.realStart), role: roleLabel(role), prio: prioText(item)
            })];
            const markerClashes = item.occurrence ? null : conflictMap.get(item.id);
            if (markerClashes && markerClashes.length) {
                marker.classList.add('is-conflict');
                parts.push(t('cal.conflictsWith', { names: namesList(markerClashes) }));
            }
            if (item.status === 'COMPLETED') {
                marker.classList.add('is-completed');
                parts.push(t('status.COMPLETED'));
            }
            if (item.occurrence) {
                marker.classList.add('is-repeat');
                parts.push(t('cal.weeklyRepeat'));
                makeActivatable(marker, () => openPreview(item, marker));
            } else {
                parts.push(t('cal.clickDetails'));
                makeActivatable(marker, () => openPreview(item, marker));
            }
            const description = describe(parts);
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

        const parts = [t('cal.blockDesc', { title: item.title, time: timeText, role: roleLabel(role), prio: prioText(item) })];

        // Conflicts are computed for the real item only, so a repeat never shows the original's badge
        const clashes = item.occurrence ? null : conflictMap.get(item.id);
        if (clashes && clashes.length) {
            block.classList.add('is-conflict');
            parts.push(t('cal.conflictsWith', { names: namesList(clashes) }));
        }
        if (item.status === 'COMPLETED') {
            block.classList.add('is-completed');
            parts.push(t('status.COMPLETED'));
        }

        if (item.occurrence) {
            // A generated repeat is read-only: the series is changed through the original item
            block.classList.add('is-repeat');
            parts.push(t('cal.weeklyRepeat'));
            makeActivatable(block, () => openPreview(item, block));
        } else {
            if (item.recurring) parts.push(t('repeat.title'));
            parts.push(t('cal.clickDetails'));
            block.dataset.originalId = item.id;   // KAN-363: found again by "Go to original"
            makeActivatable(block, () => openPreview(item, block));
        }

        const description = describe(parts);
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
        head.append(el('h2', 'cal-summary-title', t('cal.weekly.title')));

        const due = data.completedTasks + data.missedTasks;
        const rateText = due === 0
            ? t('cal.weekly.nothingDue')
            : t('cal.weekly.rate', { done: data.completedTasks, due, pct: Math.round(data.completedTasks / due * 100) });
        head.append(el('p', 'cal-summary-rate', rateText));

        const stats = el('div', 'cal-stats');
        stats.append(
            statTile('is-completed', data.completedTasks, t('status.COMPLETED')),
            statTile('is-missed', data.missedTasks, t('cal.weekly.missed')),
            statTile('is-upcoming', data.upcomingTasks, t('cal.weekly.upcoming'))
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
            ? t('cal.weekly.noReviews')
            : t(data.daysReviewed === 1 ? 'cal.weekly.prodOne' : 'cal.weekly.prodMany',
                { score: data.averageProductivityScore ?? '—', n: data.daysReviewed });

        summaryEl.append(head, stats, bar, el('p', 'cal-summary-reviews', reviewText));

        // KAN-341: busiest day (left out when nothing happens this week)
        if (data.busiestDay) {
            const [y, m, d] = data.busiestDay.split('-').map(Number);
            const label = new Date(y, m - 1, d).toLocaleDateString(loc(), { weekday: 'long', month: 'short', day: 'numeric' });
            const count = `${data.busiestDayCount} ${t(data.busiestDayCount === 1 ? 'task.one' : 'task.many')}`;
            summaryEl.append(el('p', 'cal-summary-reviews cal-summary-busiest', t('cal.weekly.busiest', { label, count })));
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
        setStatus(t('cal.loading'));
        grid.setAttribute('aria-busy', 'true');

        try {
            const query = `from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`;
            const [items, conflicts, reminders] = await Promise.all([
                apiFetch(`/api/schedule-items/range?${query}`),
                apiFetch('/api/schedule-conflicts/me'),
                apiFetch('/api/reminders').catch(() => [])   // KAN-382: a failed reminder list must not break the calendar
            ]);

            // Ignore an older response if the user already clicked next/previous again
            if (requestId !== state.requestId) return;

            state.conflicts = buildConflictMap(conflicts);
            state.reminders = reminders.filter(r => !r.scheduleItemId);   // KAN-382: task reminders are already on their task
            render(days, items, state.conflicts);
            loadWeekSummary(days, requestId);
            openPendingOriginal(items);
            if (state.flash) {
                setStatus(state.flash.message, state.flash.isError);
                state.flash = null;
            } else if (items.length === 0) {
                setStatus(t(state.view === 'day' ? 'cal.emptyDay' : 'cal.emptyWeek'));
            } else {
                setStatus('');
            }
        } catch (error) {
            if (error.sessionExpired || requestId !== state.requestId) return;
            console.error(error);
            render(days, [], new Map());
            setStatus(t('cal.loadFailed'), true);
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

    // KAN-382: redraw the reminder markers when reminders change in the bell or the popup
    window.addEventListener('aalsikke:bell-changed', () => load());
    window.addEventListener('aalsikke:popup-changed', () => load());

    async function loadUnscheduledTasks() {
        const all = await apiFetch('/api/schedule-items/me');
        return all
            .filter(item => !item.startTime && item.status !== 'COMPLETED')
            .sort((a, b) => new Date(a.deadline) - new Date(b.deadline));
    }
    // KAN-366: "Create a new task here" opens the Tasks page's New task form with the date and
    // times currently in this window (tasks.html?date=YYYY-MM-DD&start=HH:MM&end=HH:MM#new-task).
    function newTaskLink(text) {
        const link = el('a', 'modal-link', text);
        link.href = 'tasks.html#new-task';
        link.addEventListener('click', event => {
            event.preventDefault();
            const date = dateInput.value || '';
            const start = startInput.value || '';
            const end = startOnlyInput.checked ? '' : (endInput.value || '');
            const params = new URLSearchParams();
            if (date) params.set('date', date);
            if (start) params.set('start', start);
            if (end && end > start) params.set('end', end);
            window.location.href = `tasks.html?${params}#new-task`;
        });
        return link;
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
            modalTitle.textContent = t(scheduled ? 'cal.sched.reschedule' : 'cal.sched.schedule1');
            taskField.hidden = true;
            taskName.hidden = false;
            taskName.textContent = item.deadline
                ? t('cal.sched.itemDue', {
                    title: item.title,
                    date: formatShortDate(new Date(item.deadline)),
                    time: formatTime(new Date(item.deadline))
                })
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
            modalTitle.textContent = t('cal.sched.title');
            taskField.hidden = false;
            taskName.hidden = true;
            fillTimes(begin, new Date(begin.getTime() + DEFAULT_BLOCK_MINUTES * 60000));

            taskSelect.replaceChildren(new Option(t('tasks.loading'), ''));
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
                    taskSelect.append(new Option(t('cal.sched.noneUnscheduled'), ''));
                    setModalMessage('', 'info');
                    modalMessage.append(
                        t('cal.sched.noneMsg1'),
                        newTaskLink(t('cal.newTaskHere')),
                        t('cal.newTaskEmptyTail')
                    );
                    return;
                }
                for (const task of tasks) {
                    const due = task.deadline ? t('cal.sched.optionDue', { date: formatShortDate(new Date(task.deadline)) }) : '';
                    taskSelect.append(new Option(`${task.title}${due}`, task.id));
                }
                setModalMessage('', 'info');
                modalMessage.append(t('cal.newTaskOr'), newTaskLink(t('cal.newTaskInline')), t('cal.newTaskWithDateTime'));
                taskSelect.disabled = false;
                saveButton.disabled = false;
                taskSelect.focus();
            } catch (error) {
                if (error.sessionExpired) return;
                console.error(error);
                taskSelect.replaceChildren(new Option(t('cal.sched.loadTasksFailed'), ''));
                setModalMessage(t('cal.sched.loadTasksFailedMsg'), 'error');
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
            setModalMessage(t('cal.sched.chooseTask'), 'error');
            return;
        }
        const startOnly = startOnlyInput.checked;

        if (!dateInput.value || !startInput.value || (!startOnly && !endInput.value)) {
            setModalMessage(t(startOnly ? 'cal.sched.pickStart' : 'cal.sched.pickAll'), 'error');
            return;
        }

        const start = fromInputs(dateInput.value, startInput.value);
        const end = startOnly ? null : fromInputs(dateInput.value, endInput.value);

        // Client-side check required by S3-12 (the backend checks it too)
        if (end && end <= start) {
            setModalMessage(t('cal.sched.endAfterStart'), 'error');
            endInput.focus();
            return;
        }

        saveButton.disabled = true;
        setModalMessage(t('common.saving'), 'info');

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
                setModalMessage(t('cal.sched.notYours'), 'error');
                saveButton.disabled = false;
                return;
            }
            if (!response.ok) {
                setModalMessage(errorMessageFrom(body, t('cal.sched.saveFailed')), 'error');
                saveButton.disabled = false;
                return;
            }

            // TODO(S3-09): show Yara's shared conflict warning here once it is merged,
            // instead of this plain text. body.conflicts = [{ id, title, startTime, endTime }]
            const conflicts = (body && body.conflicts) || [];
            const slotText = end
                ? `${formatShortDate(start)}, ${formatTime(start)}–${formatTime(end)}`
                : t('cal.at', { date: formatShortDate(start), time: formatTime(start) });
            const savedTitle = (body && body.title) || title;
            state.flash = conflicts.length
                ? {
                    message: t('cal.sched.scheduledOverlap', {
                        title: savedTitle,
                        slot: slotText,
                        names: conflicts.map(c => `“${c.title}”`).join(t('common.listSep'))
                    }),
                    isError: true
                }
                : { message: t('cal.sched.scheduled', { title: savedTitle, slot: slotText }), isError: false };

            closeScheduleForm();

            // Jump to the day that was scheduled so the block is visible
            state.anchor = startOfDay(start);
            state.scrollPending = true;
            load();
        } catch (error) {
            if (error.sessionExpired) return;
            console.error(error);
            setModalMessage(t('cal.sched.saveNetwork'), 'error');
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
                setStatus(t('cal.sched.notFound'), true);
            }
        } catch (error) {
            if (!error.sessionExpired) console.error(error);
        }
    }

    load();
    openFromUrl();
});