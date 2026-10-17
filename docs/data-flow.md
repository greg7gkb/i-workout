# Data flow

This page shows how data moves onto and off the phone. Each participant is a swim lane. The rules behind it are in [FOUNDATION.md](FOUNDATION.md) §5, and field IDs are in [airtable-schema.md](airtable-schema.md).

```mermaid
sequenceDiagram
    autonumber
    participant C as Claude<br/>(Airtable connector)
    participant AT as Airtable<br/>(i-workout base)
    participant SW as Phone: SyncWorker<br/>(background, online only)
    participant DB as Phone: Room DB<br/>(local copy)
    participant UI as Phone: App UI<br/>(you)

    rect rgba(100, 150, 250, 0.12)
    Note over C,AT: 1. PLAN: Claude writes routines
    C->>AT: Create/update Routines + Exercises
    end

    rect rgba(100, 200, 120, 0.12)
    Note over AT,UI: 2. SYNC: on app launch, every 6h, Sync button, Finish/Discard
    Note over SW: Waits until the phone is online
    SW->>DB: pushable(): finished/discarded Sessions that are dirty
    DB-->>SW: Sessions + their Sets
    loop Batches of up to 10
        SW->>AT: PATCH Sessions (upsert on UUID, Sets as JSON text)
        AT-->>SW: 200 OK
        SW->>DB: markClean(uuid, revision)
        Note right of DB: Skipped if edited mid-push,<br/>so it uploads next time
    end
    SW->>AT: GET Routines (all pages)
    SW->>AT: GET Exercises (all pages)
    AT-->>SW: Plan rows
    SW->>DB: replaceAll(routines, exercises) in one transaction
    SW-->>UI: Status line: "Synced 2 min ago" or error
    end

    rect rgba(250, 180, 80, 0.12)
    Note over DB,UI: 3. LOG: fully offline, UI only talks to Room
    DB-->>UI: Active Routines
    UI->>DB: Start workout (new Session, dirty)
    DB-->>UI: Exercises, targets, "last time" Sets
    UI->>DB: Log set / tick warm-up / Remove (Session dirty, revision+1)
    UI->>DB: Finish or Discard (endedAt or deleted, dirty)
    UI-)SW: syncNow() goes to step 2 once online
    DB-->>UI: "1 workout waiting to upload"
    end

    rect rgba(180, 120, 220, 0.12)
    Note over C,AT: 4. REVIEW: Claude reads your logs
    C->>AT: Read Sessions (ignore Deleted) + Routines
    AT-->>C: Logged workouts
    Note over C: Writes the next Routines (back to step 1)
    end
```

## Key rules

- **Plans flow down, logs flow up.**
  - Routines and Exercises: Claude → Airtable → phone. Airtable always wins.
  - Sessions and Sets: phone → Airtable → Claude. Only the phone writes them.
- **Every sync runs in the same order: push, then pull.**
  - The push is an upsert keyed on UUID, so retries never create duplicate rows.
  - The pull replaces the whole plan, so anything Claude deletes in Airtable also disappears from the phone.
- **The UI never talks to Airtable.** Every screen reads and writes Room, so the app behaves the same with no signal.
- **A workout in progress never leaves the phone.** A Session becomes pushable only after Finish or Discard.
