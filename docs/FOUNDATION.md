# i-workout: Foundation

Status: **Agreed model, v1.** Update this doc whenever a decision changes.

## 1. Purpose

A small personal Android app for logging strength workouts. Claude writes the routines. The app's only jobs are to show what to do, make logging fast in the gym (including with no signal), and keep a history Claude can read when writing the next routines.

## 2. Objectives

1. **Fast logging.** Logging a set takes one or two taps, and the screen shows what you did last time.
2. **Offline first.** Every feature works with no network. Sync happens later, on its own.
3. **Claude writes the routines.** Claude creates and revises routines directly in Airtable. The app never needs an update to get a new routine.
4. **Small and light.** One user, one phone, one Airtable base. As few dependencies as possible, no custom server, and no login system.
5. **Data you can see.** Everything lives in Airtable, where you and Claude can look at it or fix it by hand.

## 3. Vocabulary

There are four terms. Two describe **the plan** (written by Claude) and two describe **the log** (written by the phone).

### Routine
A workout template: one visit to the gym. Example: "Strength Day One".
- **name**
- **notes**: free text for general information like total duration, default rest, coaching notes and "do this once today" reminders.
- an **ordered list of Exercises**

### Exercise
One item in a Routine, in a fixed position.
- **name**: e.g. "Goblet Squat", "Warm-up"
- **type**: one of `warm-up`, `cool-down`, `equipment`, `bodyweight`
- **instructions**: *brief* focal points, e.g. "Hips back, flat back, hinge not squat"
- **sets**: target number of sets, e.g. `2`. Blank for warm-up and cool-down.
- **reps**: target as free text, e.g. `10`, `6 each leg`, `8 each side`
- **weight**: target as free text, e.g. `20–25 lb`, `light`, `no load`

An Exercise belongs to exactly one Routine. There's no shared exercise catalog: if the same movement appears in two Routines, it appears there twice. "Last time" matches on the exact name, so Claude keeps names consistent.

**Warm-up and cool-down are each a single Exercise.** Their sub-items go in `instructions`, e.g. "Leg swings 10/leg · glute bridges 15 · walking lunges 8/leg · cat-cow + hip circles 1 min". They're logged as one "done" tick.

### Session
One workout you actually did, on a date. Example: "Mon Oct 13, Strength Day One".
- **id**: a UUID generated on the phone
- **date** (start time; end time optional)
- **routine**: the Routine it was done from
- **notes**
- its **Sets**

### Set
One logged effort for one Exercise within a Session.
- **exercise**: which Exercise
- **reps**: actual count, as a number. Blank for warm-up/cool-down.
- **weight**: actual weight in lb, as a number. Blank for bodyweight, warm-up and cool-down.
- **done**: ✓

Warm-up and cool-down produce one Set each, with only `done` filled in.

### Diagram

```
┌──────────────────┐ 1      * ┌──────────────────────┐
│ ROUTINE          │─────────▶│ EXERCISE             │
│ name             │ ordered  │ name                 │
│ notes            │          │ type                 │
└────────▲─────────┘          │ instructions         │
         │                    │ sets · reps · weight │
         │                    └──────────▲───────────┘
         │ done from                     │ for
┌────────┴─────────┐ 1      * ┌──────────┴───────────┐
│ SESSION          │─────────▶│ SET                  │
│ id · date        │          │ reps · weight · done │
│ notes            │          └──────────────────────┘
└──────────────────┘
```

The plan is on top and the log is underneath. Logging a Set ties a Session to an Exercise.

### Out of scope for now
Programs or phases, schedules ("Monday", "next session Friday"), progression rules, and fatigue/readiness scores. Anything like that goes in the Routine's notes. Claude handles progression by writing new Routines.

## 4. Worked example: "Strength Day One"

Source: `docs/example-training-program.html`.

```
ROUTINE  "Strength Day One"
 notes:  ~40 min · rest 90 s · finish every set with 4–5 reps in reserve ·
         film one set of RDL + split squat
 │
 ├─ 1  Warm-up                 warm-up     "Leg swings 10/leg · glute bridges 15 ·
 │                                          walking lunges 8/leg · cat-cow + hip circles 1 min"
 ├─ 2  Goblet Squat            equipment   "Weight at chest, sit between knees"     2 × 10         20–25 lb
 ├─ 3  Bulgarian Split Squat   bodyweight  "Back foot up, lower straight down"      2 × 6 each leg no load
 ├─ 4  Romanian Deadlift       equipment   "Hips back, flat back, hinge not squat"  2 × 8          light
 ├─ 5  Dumbbell Row            equipment   "Pull elbow to hip"                      2 × 10 each side
 ├─ 6  Dead Bug                bodyweight  "Low back pressed flat"                  2 × 8 each side
 └─ 7  Cool-down               cool-down   "Couch stretch 60 s/side · figure 4 60 s/side"
```

A completed Session from this Routine has 12 Sets: 1 warm-up tick, 2 Sets for each of the five main Exercises, and 1 cool-down tick.

## 5. Ownership & sync

| Data | Written by | Direction |
|---|---|---|
| Routines, Exercises | Claude (Airtable connector), occasionally you by hand | Airtable → phone (Airtable wins) |
| Sessions, Sets | Phone | phone → Airtable |

**Each piece of data has exactly one writer, so there are no sync conflicts.**
- **Logs:** the phone generates the IDs. A retry or an edit updates the same row (an upsert keyed on the UUID). Deletes are soft: a `deleted` flag that syncs like any other change.
  - Only **finished or discarded** Sessions are pushed. A workout in progress stays on the phone.
  - A discard of a Session that was **never uploaded** is a local hard delete, so it never reaches Airtable. Once a Session has been uploaded, deleting it is a soft delete that syncs.
  - Each push also writes a human-readable **Summary** field (see airtable-schema.md). It's an intentional, experimental duplicate of Sets.
  - Each Session has a local `revision` counter. After a push, the Session is marked clean only if its revision hasn't changed in the meantime, so an edit made during an upload is never lost.
  - Finish and Discard trigger a sync right away (it runs once the phone is online). The Routines screen shows how many workouts are waiting to upload.
- **Plans:** each sync re-downloads all Routines and Exercises and replaces the local copy in one transaction. The plan is a few dozen rows, so this costs about one request per table, and it also picks up anything Claude deleted in Airtable (a delta pull would miss deletions).

## 6. Airtable layout

All tables live in one base, **"i-workout"**, in the **ClaudeSpace** workspace.

| Table | One row per | Key fields |
|---|---|---|
| **Routines** | Routine | Name, Notes, Active, Last Modified |
| **Exercises** | Exercise | Routine (link), Position, Name, Type (single select), Instructions, Sets, Reps, Weight, Last Modified |
| **Sessions** | Session | UUID, Started, Ended, Routine (link), Notes, Sets (JSON), Deleted, Last Modified |

Field IDs are in [airtable-schema.md](airtable-schema.md).

**Decided: Sets aren't their own table.** They're stored as JSON text in the Session row's `Sets` field. The project must stay on Airtable's **free tier**. A separate Sets table would need about 1,700 rows a year, while JSON needs about 150 Session rows a year. On the phone, Sets are still proper Room rows. The JSON is only how they're stored in Airtable.

Format: one object per logged Set, in order. A skipped Set has no entry. Warm-up and cool-down carry only `done`.

```json
[
  {"pos":1, "exercise":"Warm-up",               "done":true},
  {"pos":2, "exercise":"Goblet Squat",          "set":1, "reps":10, "weight":25},
  {"pos":2, "exercise":"Goblet Squat",          "set":2, "reps":8,  "weight":25},
  {"pos":3, "exercise":"Bulgarian Split Squat", "set":1, "reps":6},
  {"pos":7, "exercise":"Cool-down",             "done":true}
]
```

- `pos`: the Exercise's position in the Routine.
- `exercise`: the Exercise's name, used for "last time" matching.
- `set`: the set number, starting at 1.
- `reps` / `weight`: numbers. `weight` is in lb and left out for bodyweight.

Trade-off we accepted: in the Airtable UI, the log is one text cell, so it's hard to browse or filter there. You read your history in the app or by asking Claude.

## 7. Functional requirements (v1)

1. **Pick a Routine** from the active ones, newest first (by when Claude created it in Airtable).
2. **Log a Session:**
   - walk through the Exercises in order
   - see the targets, the instructions and "last time"
   - log each Set with one tap (pre-filled from last time or the target, editable)
   - add or remove Sets
   - tick warm-up and cool-down as done
3. **History:** past Sessions and their Sets, plus per-Exercise history (matched by name).
4. **Edit and delete** your own logs:
   - fix a Set's numbers, add or remove Sets
   - delete a workout (soft delete)
   - change a workout's date. Start and end move together, which also covers backfilling past workouts.
   - "Last time" always means the latest workout *before* the one you're viewing.
5. **Sync:** automatic when online, plus a manual "sync now". Shows the last sync time and the number of unsent items.

**Not in v1:** editing Routines on the phone, charts, cardio, wearables, notifications, multiple users, iOS.

## 8. Non-functional & technical

- **Platform:** Android only, on your own phone, sideloaded. Pounds by default.
- **Stack:**
  - Kotlin, Jetpack Compose and Material 3 for the UI
  - Room as the local single source of truth
  - WorkManager for sync
  - Retrofit, OkHttp and kotlinx.serialization for the web calls
  - one `app` module, with dependencies wired by hand (no DI framework)
- **Airtable API:**
  - log upserts use `performUpsert` on `UUID`
  - at most 10 records per write request
  - about 5 requests per second
- **Security:**
  - The repo is public. The Airtable token lives in `local.properties` (gitignored) and is injected at build time.
  - The token is scoped to the i-workout base, with record read/write access only.
- **Repo:**
  - This directory, pushed to a public GitHub repo under `greg7gkb`.
  - All project notes and decisions live in the repo (`CLAUDE.md`, `docs/`).
