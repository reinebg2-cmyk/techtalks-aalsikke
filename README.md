# Aalsikke Backend

Spring Boot productivity application.



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