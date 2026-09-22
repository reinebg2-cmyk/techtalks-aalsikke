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