# AshReXcue Phase D: Proactive recovery automation

Phase D adds low-noise background monitoring around the native Recovery Center.
It does not create a second AshReXcue client or poll root independently from the
existing typed manager.

## Health checks

- A unique WorkManager periodic task refreshes the shared `AshReXcueManager`.
- The default interval is six hours and can be changed to 1, 3, 6, 12, or 24 hours.
- Checks require neither network access nor charging, because boot protection is a
  local safety feature.
- A one-time check runs after boot, after app replacement, when alert preferences
  change, and when the user chooses **Check now**.
- Disabling health checks cancels scheduled work and clears AshReXcue notifications.

## Alerts

The worker derives typed signals from the live snapshot and lifecycle state:

- active rescue incidents;
- staged AshReXcue changes that require a reboot;
- restoration trials that need completion or rollback.

Each alert has a stable fingerprint. Identical state is deduplicated and only
repeated after an appropriate reminder interval. Status changes notify immediately.
Cached snapshots never clear or replace live incident state.

Notifications open the native Recovery Center and include a **Check now** action.
When a previously live incident disappears, MMRL reports the stable state once.

## Preferences

MMRL-owned automation settings are stored in `UserPreferences` with new protobuf
fields 66 through 70. Module-owned rescue thresholds remain in AshReXcue settings.
This keeps scheduling and notification policy separate from boot-time rescue logic.

## Safety properties

- One unique periodic job and one replaceable immediate job.
- No network constraint.
- No notification for a missing module on a fresh MMRL install.
- Incident and restoration alerts require a live snapshot.
- Reboot reminders can be derived from staged module lifecycle state.
- Root or transport failures are recorded for retry without manufacturing alerts.
