# AshReXcue Phase F: Recovery Plans and Safety Guardrails

Phase F converts individual guided actions into explicit recovery plans that are previewed, checked, confirmed, and rebound to live module state before execution.

## Recovery plans

The Recovery Center now builds four plan shapes:

- **Conservative** restores one lowest-risk quarantined module.
- **Balanced** restores the lowest-risk half, capped at four modules.
- **Rapid** restores every eligible quarantined module and is intentionally treated as high risk.
- **Custom** restores only the folders selected by the user.

Plans are derived from the Phase E culprit ranking and the current typed snapshot. They are never scheduled or executed automatically.

## Safety preflight

Every plan carries visible guards. Execution is blocked when:

- the installed module does not advertise `recovery-plans`;
- the snapshot is missing, stale, or cached;
- another restoration trial is active;
- no module is selected;
- the batch exceeds eight modules;
- a selected module is no longer quarantined;
- a quarantine record is stale or missing its disable marker;
- a selected module is missing from the live module inventory;
- a protected module is selected; or
- the recovery revision changed after plan creation.

Trusted modules and full-quarantine plans receive explicit warnings. Plans of five or more modules require the exact typed phrase `RESTORE N MODULES`.

## Revision-bound execution

AshReXcue 11.3.0 exposes a deterministic `recoveryRevision` in snapshot schema 1. The revision covers the quarantine records and active restoration state.

The companion app:

1. builds a plan from a live snapshot;
2. refreshes live state immediately before execution;
3. rejects a changed revision; and
4. sends the plan ID, expected revision, and exact folder list through the typed AIDL service.

The root module then acquires its state lock, recomputes the revision, validates every selected quarantine record, rejects protected or stale entries, and only then removes disable markers. This closes the plan-preview race without adding a generic root command.

## Rollback strategy

A guarded plan creates the existing restoration trial with additional plan metadata. A failed or unfinished boot disables only the tested modules and returns them to quarantine. Unrelated modules are not escalated when a restoration trial rollback succeeds.

## Compatibility and storage

- Module version: `11.3.0` (`versionCode=230`).
- Capability: `recovery-plans`.
- Snapshot schema remains version 1 with the additive `recoveryRevision` field.
- No Room entity, DAO, schema, or migration change is required.
- Plan executions are recorded through the existing activity table and appear as restoration sessions.
