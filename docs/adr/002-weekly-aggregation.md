# ADR-002: Weekly Review Aggregation Approach

**Status:** Accepted

## Context
The project brief calls for a "weekly review" feature, but the underlying database table, `daily_reviews`, is structured per single day — one row per user per day (`review_date DATE NOT NULL`, unique per user+date), with a `productivity_score` and free-text `notes` for that specific day. This raised an open question: should the table be restructured to store weekly data directly, or should "weekly review" be treated as a calculation performed on top of the existing daily data?

## Decision
The team will keep `daily_reviews` structured per day, and treat "weekly review" as an **aggregation performed over a date range**, not a separate stored entity. The existing endpoint `GET /api/daily-reviews/me?start={date}&end={date}` already supports querying a range of daily rows; a weekly view is produced by calling this endpoint with a 7-day range and aggregating the results (e.g., averaging `productivity_score`, combining `notes`) at the application or presentation layer.

## Rationale
- Preserves daily granularity — a user can still see and edit a specific day's notes and score, which would be lost if the table stored only weekly summaries.
- No schema migration required; the date-range query was tested and confirmed working during Sprint 1 (`DailyReviewRepository.findByUserIdAndReviewDateBetween`).
- A "weekly review" is naturally just one of several possible aggregation windows (a user might also want a monthly view) — keeping the underlying data daily and aggregating on demand supports this without further schema changes.

## Consequences
- The feature that builds the weekly review UI (Sprint 2/3) must implement the aggregation logic (averaging scores, combining notes) itself — this isn't precomputed or stored anywhere.
- If aggregation logic ends up needed in multiple places (e.g., a dashboard summary and a dedicated weekly review page), it should be implemented once, likely as a service-layer method, rather than duplicated per endpoint.