# ADR-001: Migration-First Database Development

**Status:** Accepted

## Context
Early in Sprint 1, a question was raised about whether the team should use a model-first approach (writing Java `@Entity` classes and letting Hibernate auto-generate the database schema) or a migration-first approach (writing explicit SQL migration files, with Java entities written to match). The team's guide document specified Flyway-managed migrations with `spring.jpa.hibernate.ddl-auto=validate` — meaning Hibernate checks that entities match the database, but never creates or alters tables itself. In practice, the team followed this migration-first approach throughout Sprint 1 (V1 through V9 Flyway migrations), but it was never formally written down as an agreed decision.

## Decision
The team will use **migration-first** development: all database schema changes are written as explicit, versioned Flyway SQL migration files (`src/main/resources/db/migration/V{n}__description.sql`). Java entity classes are written and updated to match the schema defined in migrations — never the other way around.

## Policy
- Flyway is the single source of truth for database schema. `spring.jpa.hibernate.ddl-auto` remains set to `validate` — Hibernate never creates or modifies tables automatically.
- Every schema change (new table, new column, altered constraint) requires a new migration file with the next sequential version number. Existing, already-applied migration files are never edited — corrections go in a new migration.
- Direct changes to a production or shared database (via a GUI tool, manual `ALTER TABLE`, etc.) are prohibited. All schema changes must go through a committed migration file so every environment stays in sync and the change is tracked in version control.
- Local developer databases should be dropped and recreated when migrations are significantly reworked, rather than manually patched, to avoid drift.

## Consequences
- Full control over exact SQL, including Postgres-specific features (UUID defaults, CHECK constraints) that auto-generation tools handle less predictably.
- Requires developers to write SQL directly rather than relying on Hibernate to infer schema from entities — a small amount of extra manual work per schema change, in exchange for precision and auditability.
- Schema history is fully visible and versioned in the repository, matching what the team already experienced firsthand when several schema redesigns happened during Sprint 1.