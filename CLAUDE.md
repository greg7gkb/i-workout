# CLAUDE.md

Personal offline-first Android workout logger. Claude writes training programs into Airtable; the app logs workouts and syncs.

- **Source of truth for requirements & vocabulary:** `docs/FOUNDATION.md`. Use its entity names exactly (Routine, Exercise, Session, Set).
- **Self-contained repo:** all project notes, decisions, and memory live in this repo (this file or `docs/`), never in Claude's external memory directory. Record durable decisions here or in `docs/`.
- **Public repo** (GitHub user `greg7gkb`): never commit secrets. The Airtable token lives in `local.properties` (gitignored).
- **Keep it small:** single `app` module, minimal dependencies, no DI framework.
