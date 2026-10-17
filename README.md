# Aal Sikke

**TechTalks Summer Internship 2026 — Team Java D**

An adaptive management platform that organizes a user's Student and Work life in one place — one account, connected planning across both contexts.

## Team
- Reine Bou Ghanem
- Ashraf Rasbieh
- Yara Abou Ezzeddine
- Zeinab Kassir

**Mentor:** Khaled Frayji

## Concept
AAl sikke is designed as a shared-core platform that can activate different life-management contexts (Student, Work, and eventually Business, Traveler, Teacher, General User). For this internship, scope is intentionally narrowed to a focused MVP.

**Target segment:** University students who also work part-time or freelance.

**Positioning:** An adaptive management platform that organizes a user's personal, academic, professional, business, and travel life in one place — one user, multiple roles, one connected platform.

## Approved V1 Scope
- Secure authentication (each user sees only their own data)
- Student and Work as **planning contexts** within one account (not separate permission roles)
- Task categories with deadlines, priorities, completion status, and reminders
- One unified daily/weekly dashboard and calendar
- Scheduling-conflict warnings (triggered by commitments with start/end times, including scheduled task blocks — not by deadlines alone)
- Simple weekly review
- In-app reminders shown on app open (external notifications deferred)

**Explicitly excluded from V1:** profession-specific modules, finance management, document storage, AI features, payments, full offline sync, business-owner tools, travel planning.

## Tech Stack
- **Backend:** Java, Spring Boot, Spring Security, JWT authentication, role-based authorization
- **API:** REST
- **Database:** PostgreSQL, Spring Data JPA, Hibernate, Flyway migrations
- **Other:** File upload/management, scheduled tasks (planned for later phases)

## Differentiation
Competitor products (e.g. Tarteeb) already offer many life categories, calendars, and offline support. Aal Sikke's differentiation is not module count but **connected cross-role planning** — a clearer, more coherent experience specifically for working students juggling both academic and job commitments in one view.

## Process & Status
- Assignment 1 (Product Discovery & Market Validation) submitted, revised per mentor feedback, and **approved for implementation**.
- Still required alongside development: ~5 focused interviews with the target segment, and a tested clickable Student+Work flow with 10 target users (anonymized notes + a brief summary of findings, no recordings needed).
- Team workflow: shared GitHub repo + Jira Scrum. Sprint 1 focuses on adding Student and Work commitments and viewing them together.
- Each team member should have a specific assigned contribution (tracked in project docs, not "All Tasks" for everyone).

---

# Backend developer guide

Spring Boot productivity application.



## Testing emails locally (Mailpit)

By default the app sends no emails: verification links are only written to the console by `LoggingEmailSender`, for example:

```
[EMAIL] Verify you@test.com -> http://localhost:8080/verify-email.html?token=...
```

The other mail defaults are for Gmail (port 587, SMTP login and STARTTLS required). Mailpit runs on your own machine without login or encryption, so it fails with those defaults.

To send real emails to Mailpit:

1. Start Mailpit by running `mailpit.exe` from the folder where you installed it. The inbox is at http://localhost:8025 and SMTP listens on port 1025.
2. Start the app with these 5 environment variables:

   | Variable | Value |
   | --- | --- |
   | `MAIL_ENABLED` | `true` |
   | `MAIL_HOST` | `localhost` |
   | `MAIL_PORT` | `1025` |
   | `MAIL_SMTP_AUTH` | `false` |
   | `MAIL_STARTTLS` | `false` |

   In PowerShell (the variables only last for that window):

   ```powershell
   $env:MAIL_ENABLED='true'; $env:MAIL_HOST='localhost'; $env:MAIL_PORT='1025'; $env:MAIL_SMTP_AUTH='false'; $env:MAIL_STARTTLS='false'; ./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
   ```

   In IntelliJ: Run → Edit Configurations → Environment variables:

   ```
   MAIL_ENABLED=true;MAIL_HOST=localhost;MAIL_PORT=1025;MAIL_SMTP_AUTH=false;MAIL_STARTTLS=false
   ```

3. Register a new account. The `[EMAIL]` line no longer appears in the console, and the verification email shows up in Mailpit.

To send through a real Gmail account instead, set `MAIL_ENABLED=true`, `MAIL_USERNAME` and `MAIL_PASSWORD` (a Gmail app password) and keep the other defaults.

## Running with Docker / production (KAN-358, KAN-359)

The `prod` profile (`application-prod.properties`) reads all configuration from environment variables; nothing secret is in git. If a required variable is missing or empty, the app refuses to start and names the missing ones.

| Variable | Required | Notes |
|---|---|---|
| `DB_URL` | yes | JDBC format: `jdbc:postgresql://host:5432/dbname?sslmode=require` (not `postgres://...`) |
| `DB_USERNAME` | yes | |
| `DB_PASSWORD` | yes | |
| `JWT_SECRET` | yes | Random, at least 32 characters. Changing it logs everyone out. |
| `APP_BASE_URL` | yes | Public address used in email links, e.g. `https://your-domain` (no trailing slash) |
| `MAIL_USERNAME` | yes | SMTP login (Gmail: the full Gmail address) |
| `MAIL_PASSWORD` | yes | SMTP password (Gmail: an App Password, not the account password) |
| `MAIL_FROM` | yes | Sender shown to users; must be an email address |
| `MAIL_HOST` / `MAIL_PORT` | no | Default `smtp.gmail.com` / `587` |
| `PORT` | no | Default `8080` (some platforms set it themselves) |
| `DB_POOL_SIZE` | no | Default `5` |

**Generate a JWT secret** (PowerShell, cryptographically random):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

**Gmail App Password:** the sending Gmail account needs 2-Step Verification turned on. Then search for "App passwords" in the Google Account settings, create one, and use it as `MAIL_PASSWORD`.

**Build and run the image locally:**

```powershell
docker build -t aalsikke:local .
```

Set the variables in the same PowerShell window (`$env:DB_URL = "..."` and so on), then pass them by name so no secret appears in the command:

```powershell
docker run --rm -p 8080:8080 -e DB_URL -e DB_USERNAME -e DB_PASSWORD -e JWT_SECRET -e APP_BASE_URL -e MAIL_HOST -e MAIL_PORT -e MAIL_SMTP_AUTH -e MAIL_STARTTLS -e MAIL_USERNAME -e MAIL_PASSWORD -e MAIL_FROM aalsikke:local
```

Inside the container, `localhost` is the container itself: use `host.docker.internal` to reach PostgreSQL or Mailpit on your machine. With Mailpit, set `MAIL_HOST=host.docker.internal`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false`, and any non-empty `MAIL_USERNAME` / `MAIL_PASSWORD`.

On an empty database Flyway runs every migration from V1 on the first start; later starts only run new migrations, so data is kept across deploys.

## API decisions

### Updating a schedule item: `PUT /api/schedule-items/{id}` is a partial update

Agreed with the frontend in Sprint 3 (S3-06).

- Only the fields included in the request body are changed. Any field left out keeps its current value.
  Example: `PUT` with `{"title": "New title"}` changes the title and nothing else.
- This lets the edit form (and calendar time-blocking) send only what the user changed, without
  wiping fields such as `deadline` by accident.
- Sending `null` for a field, or leaving it out, means "leave unchanged". A field cannot currently be
  cleared back to empty through `PUT`.
- Only the item's owner can update it (identity comes from the JWT); otherwise the API returns `403`.

### Error responses

- Errors use the shape `{ "field": "...", "message": "..." }`, or a list of these for validation errors.
  `field` is `null` when the error is not tied to one input.
- Only messages written by the application are returned. Database, Hibernate and JWT library details
  are logged on the server and never included in API responses.



### Calendar range: `GET /api/schedule-items/range?from=...&to=...` (S3-02)

Returns the caller's items for a date range, so the calendar does not download everything.

- `from` (inclusive) and `to` (exclusive) are ISO date-times with an offset, e.g.
  `?from=2026-09-21T00:00:00Z&to=2026-09-28T00:00:00Z` for one week.
  Send UTC with `Z` (JavaScript: `date.toISOString()`). A `+03:00` offset must be URL-encoded as `%2B03:00`.
- An item is returned if its time slot overlaps the range (`startTime < to` and `endTime > from`),
  or if its `deadline` falls inside the range (so the calendar can show a deadline marker).
- Results are sorted by start time and use the same item format as `GET /api/schedule-items/me`.
- Only the caller's own items are returned (identity comes from the JWT).
- Errors (`400`): `from` not before `to`, range longer than 62 days, missing or invalid `from`/`to`.





### Conflict detection (S3-07)

- `POST` and `PUT /api/schedule-items` responses include `conflicts`: the caller's other items whose
  time slot overlaps the saved item, each as `{ id, title, startTime, endTime }`. Every other item
  response has `"conflicts": []`.
- The save is never blocked; conflicts are only a warning.
- Overlap means `start1 < end2` and `start2 < end1`. Back-to-back items (one ends exactly when the
  next starts) are not a conflict. Items without both a start and an end time (deadline-only) never
  conflict. Only the caller's own items are compared.
- `GET /api/schedule-conflicts/me` returns every overlapping pair of the caller's items as
  `{ itemOne, itemTwo }` (earlier item first). Conflicts are computed from the current times on each
  request, so they are never stale after an edit or delete. The old `schedule_conflicts` table is no
  longer used, and the manual `POST /api/schedule-conflicts` endpoint was removed.


### Deadline-only items (V11)

- `startTime` and `endTime` are optional. An item can have only a `deadline` (e.g. "Submit report,
  due Friday") and be scheduled into a slot later (time-blocking) with a partial `PUT` that sends
  `startTime` and `endTime`; the deadline is unchanged.
- Rules (400 with a clear message, also enforced by database constraints):
  `startTime` and `endTime` are set together or not at all; `endTime` must be after `startTime`;
  every item needs a time slot or a deadline.
- Deadline-only items never produce conflicts and appear in the calendar range as deadline markers.
- Dashboard lists (today, upcoming, overdue) and the overdue count use the item's "due time":
  `endTime` if it has a slot, otherwise its `deadline`.
- Known limitation: because `PUT` is a partial update, times cannot be cleared back to empty
  (an item cannot be "unscheduled" yet).




### Weekly review: `GET /api/daily-reviews/weekly?start=YYYY-MM-DD&end=YYYY-MM-DD` (KAN-248, ADR-002)

- Aggregates the caller's daily reviews for the date range (start and end inclusive, max 31 days):
  `daysReviewed`, `averageProductivityScore` (one decimal, `null` if nothing was scored) and the
  `reviews` themselves, sorted by date.
- Also counts the caller's tasks due in the same dates (due time = `endTime`, or `deadline` for
  deadline-only items): `completedTasks`, `missedTasks` (not completed and already due) and
  `upcomingTasks` (not completed, due later).
- `GET /api/daily-reviews/me` and `POST /api/daily-reviews` now return reviews without the nested user.

### Completing a task: `PATCH /api/schedule-items/{id}/status`

- Body: `{"status":"COMPLETED"}` or `{"status":"PENDING"}` (anything else → 400). Returns the updated
  item. Only the owner can change it (otherwise 403). Used by the dashboard's done checkbox.
- KAN-363: add `"occurrenceDate": "YYYY-MM-DD"` to mark one weekly repeat instead of the item itself,
  e.g. `{"status":"COMPLETED","occurrenceDate":"2026-10-23"}`. Only that week changes; the original
  and its other repeats keep their own status. A date that is not a repeat of the item gives 400.
  Returns the repeat. Stored in `occurrence_completions` (V26); deleting the item deletes them.



### Recurring items (KAN-248, weekly only)

- `POST /api/recurrence-rules` with `{ "scheduleItemId", "frequency": "WEEKLY", "intervalValue"?: 1-52,
  "daysOfWeek"?: "MON,WED", "untilDate"?: "YYYY-MM-DD" }` makes one of the caller's timed items repeat.
  Posting again for the same item replaces its rule. Only `WEEKLY` is accepted for now.
- `GET /api/recurrence-rules` lists the caller's rules.
- `GET /api/schedule-items/range` includes the generated repeats in the range with `"occurrence": true`
  (same `id` as the original item, own `startTime`/`endTime`, no deadline). The original keeps
  `"occurrence": false`. Repeats keep the original's local start time (server time zone), so 09:00
  stays 09:00 across daylight-saving changes.
- KAN-363: each repeat has its own `status` (from `occurrence_completions`, never the original's),
  its `occurrenceDate` (local date, sent back when marking it done) and `seriesStart` (the original's
  start, used by "Go to original").
- The calendar shows repeats striped with a ↻ and read-only except Mark done (that week only); the series is changed through the original ("Go to original").
- The weekly review counts the repeats due in the range too, each with its own status (KAN-363).
- Known limitations: conflict detection and the dashboard lists only look at real items, not generated
  repeats. Single repeats cannot be moved or skipped yet.