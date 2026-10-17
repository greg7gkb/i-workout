# Airtable schema

These are the IDs of the author's base, **i-workout** (`appy16TLEgUqF2KWV`). Another base has the same tables and fields with different IDs; see README → Setup. It's on the free tier. The model and its rationale are in [FOUNDATION.md](FOUNDATION.md).

IDs are listed here so code and Claude can use stable field IDs instead of names. They aren't secrets: nothing can be accessed without a personal access token, and the token is never committed.

## Routines: `tbleFsPQ00hIQOnwH` (written by Claude)

| Field | ID | Type |
|---|---|---|
| Name (primary) | `fldAQ13VPKKxHsLcE` | single line text |
| Notes | `fldnu6wSiihZlFnj6` | long text |
| Active | `fldMk7KijA21uw9do` | checkbox |
| Last Modified | `fldd0KdTbET3KR1DK` | last modified time |
| Exercises | `fld7Squ4t0HmbYuiO` | link (inverse of Exercises.Routine) |
| Sessions | `fldN74eJh8eUG1Fkk` | link (inverse of Sessions.Routine) |

## Exercises: `tblGWXvbop8XtWbB3` (written by Claude)

| Field | ID | Type |
|---|---|---|
| Name (primary) | `fld3qeijt43MM2SKZ` | single line text |
| Routine | `fld1YWtyc9HOQkbXZ` | link → Routines (exactly one) |
| Position | `fld85ej7Zvbg0mJHh` | integer, starting at 1 |
| Type | `fldFDGbsE9pDhdvVp` | single select: warm-up, equipment, bodyweight, cool-down |
| Instructions | `fldur560dab6tReaK` | long text, brief |
| Sets | `fldMTqVJifdYDe64n` | integer (blank for warm-up/cool-down) |
| Reps | `fldv0ZX1sLonJAte8` | text target |
| Weight | `fldJxPE9cCsZKWQeb` | text target |
| Last Modified | `fldbumo6Fx1hBZtEo` | last modified time |

## Sessions: `tblvitGsglPEhUFv9` (written by the phone)

| Field | ID | Type |
|---|---|---|
| UUID (primary) | `fldFDsxy3OMs31HVV` | single line text; upsert key |
| Started | `fldW2JUunfFsdtiFP` | date-time (America/Los_Angeles) |
| Ended | `fld8BeaJxT2Zh8YBK` | date-time |
| Routine | `fldw6Bds6Ot80t9fA` | link → Routines (zero or one) |
| Notes | `fldMK4JsAZG0NPBsa` | long text |
| Sets | `fld50x68oynBecEJK` | long text holding a JSON array (format: FOUNDATION.md, "Data contract") |
| Deleted | `fldLENVhPJXrh5Wor` | checkbox (soft delete) |
| App Version | `fldHgon9Df3sqD1AT` | text: the app's `versionName (versionCode)` that last uploaded the row. Tells which Sets format it was written in. |
| Summary | `fldPiPbgEZCqTo2Om` | long text: a readable copy of Sets, one exercise per line (`Goblet Squat: 10 @ 25, 10 @ 25`, `Warm-up ✓`). Written by the app. Sets stays the source of truth. Experimental; delete it if it isn't useful. |
| Last Modified | `fldliIbVD9ADGeDkn` | last modified time |

## Seed data

- Routine **Strength Day One** (`recm7y5R0Ut20xArF`), active, with 7 Exercises, positions 1–7.
- It was created from `docs/example-training-program.html`.
- Routine **Strength Day Two** (`recmHqls0LvkzMFTx`), active, with 6 Exercises: upper body + core. It matches the "next session Friday" note on that sheet.
