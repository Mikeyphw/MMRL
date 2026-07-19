# AshReXcue Phase E: Guided Recovery Intelligence

Phase E turns AshReXcue recovery evidence into explainable, user-confirmed recommendations inside MMRL's native Recovery Center.

## Guidance model

The guidance engine ranks module candidates from evidence already present in a typed AshReXcue snapshot:

- quarantine ownership and reason;
- explicit suspect, trusted, and protected classifications;
- module fingerprint changes since the last stable boot;
- mentions in rescue and failed restoration history;
- participation in the latest rescue;
- recent quarantine timestamps;
- enabled state during an active incident; and
- previously recorded recommendation outcomes.

Every score is accompanied by human-readable evidence. Protected modules receive a strong score penalty and are excluded from guided state-changing recommendations.

## Recommendations

The Recovery Center can recommend:

- restoring the lowest-risk quarantined module;
- restoring an explicit low-risk half batch when the installed module advertises `guided-recovery`;
- marking a high-confidence, not-yet-suspect module as suspect;
- reviewing an active restoration trial; or
- observing until more evidence is available.

No recommendation executes automatically. Mutating recommendations always show an exact module preview and require confirmation.

## Exact guided batches

The typed root API now supports `restoreBatch(folders)`. The app validates every folder, passes an explicit list through AIDL, and invokes:

```text
ashrexcuectl restore selected folder-a,folder-b
```

The module validates the complete list and creates a restoration trial from only those quarantine records. Older installed modules remain compatible because the app falls back to single-module restoration unless the `guided-recovery` capability is advertised.

## Snapshot additions

Module snapshot entries now expose:

- `baseTrust`;
- `fingerprint`; and
- `changedSinceStable`.

Quarantine entries expose their recorded `reason`. These fields are additive within snapshot schema 1 and are consumed with safe defaults by the companion app.

## Outcome feedback

Users can mark a recommendation as helped, failed, or inconclusive. Feedback is stored in the existing local Ash activity database and becomes explanatory evidence during later ranking. No Room schema or migration is required.

## Safety rules

- no automatic destructive action;
- protected modules are excluded from guided mutations;
- explicit batches are limited and folder validated;
- current restoration trials must be resolved before another trial starts;
- cached or incompatible module states remain read-only; and
- recommendation ranking is presented as evidence, not proof.
