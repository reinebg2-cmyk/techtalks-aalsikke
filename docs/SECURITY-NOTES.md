# Security Notes

## Incident: Local Postgres password committed to git history (KAN-102)

**Date identified:** September 2026 (Sprint 2)
**Severity:** Low
**Status:** Resolved — local rotation only, no history rewrite

### What happened
During Sprint 1, `src/main/resources/application.properties` contained a hardcoded
PostgreSQL password (`postreine21`) for local development. This was fixed by
switching to an environment-variable-based configuration
(`${DB_PASSWORD:postgres}`) in commit `04f562b`, but the original plaintext
password remains visible in git history prior to that commit.

### Investigation (KAN-116)
Confirmed via `git log --all -p -- src/main/resources/application.properties`
that the password was present in the commit history and is still retrievable
by anyone with repository access.

### Decision
Given this credential:
- Was only ever used for a **local development database**, never a production
  or shared server
- Was never tied to real user data
- Was never exposed outside the team's private repository

The team decided **not** to rewrite git history (via `git-filter-repo` or BFG)
to remove it. A full history rewrite would require every team member to
delete and re-clone their local repository, risking loss of any uncommitted
work, for a credential with negligible real-world impact.

### Actions taken instead
- Confirmed the password was already replaced with an environment-variable
  pattern in the current codebase (KAN-117, effectively — no live use of the
  old credential remains in any file).
- Each team member rotated their local PostgreSQL password to a new value not
  matching the one in history (KAN-118).
- This document serves as the record of the finding and the team's reasoning
  (KAN-119, in place of a formal access-log audit, which does not apply to a
  local-only database with no external access).
- KAN-120, KAN-121, and KAN-122 (scrub git history, git-filter-repo/BFG, force
  push) are marked **intentionally skipped** for the reasoning above.

### Going forward
- `application-local.properties` (git-ignored) and `application.properties.example`
  (a template with placeholder values) are now the standard pattern for local
  credentials — see `docs/environment-setup.md` if the team decides to add one.
- Any future credential — even for local/dev use — should go directly into a
  git-ignored file from the start, never into a tracked `.properties` file.