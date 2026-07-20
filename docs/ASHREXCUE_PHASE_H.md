# AshReXcue Phase H: Automation and External Control

Phase H exposes recovery intelligence through typed Tasker actions and a stable shell JSON protocol without adding arbitrary shell execution.

## Security model

- Read-only capability, status, and evidence queries are rate limited but do not mutate module state.
- Recovery mutations require the dedicated **Allow AshReXcue recovery control** setting.
- A recovery plan must be previewed before execution.
- Execution tokens expire after 30 minutes, are bound to one recovery revision and idempotency key, and are consumed once.
- Repeated calls return the original operation or receipt instead of starting another restoration.
- Every affected module must satisfy the configured Tasker approval policy.
- High-risk recovery plans always require explicit MMRL approval.
- The root worker refreshes live AshReXcue state and reruns Phase F guards before restoring modules.
- No action accepts an arbitrary command or script.

## Tasker actions

- AshReXcue Capabilities
- AshReXcue Status
- AshReXcue Evidence
- Prepare AshReXcue Plan
- Execute AshReXcue Plan
- Record AshReXcue Outcome
- Refresh AshReXcue Evidence

All actions return `ash_protocol_version=1`, `ash_schema=mmrl.ash.external.v1`, and a structured JSON envelope in the normal MMRL result variable.

## Shell protocol

The embedded module exposes the parallel typed interface:

```sh
ashrexcuectl automation capabilities
ashrexcuectl automation status
ashrexcuectl automation evidence needs-review
ashrexcuectl automation prepare conservative task:recovery-001 "" true
ashrexcuectl automation prepare custom task:recovery-002 alpha,beta false
ashrexcuectl automation execute <token> task:recovery-002
ashrexcuectl automation outcome recommendation-1 alpha helped task:outcome-001
ashrexcuectl automation receipt task:recovery-002
```

The shell protocol uses schema `ashrexcue.external.v1` and stable exit codes:

| Code | Meaning |
|---:|---|
| 0 | Success or idempotent replay |
| 1 | Runtime failure |
| 2 | Invalid input |
| 3 | Recovery state busy |
| 4 | Conflict, denial, blocked plan, or missing receipt |
| 5 | Rate limited |
| 6 | Token missing, consumed, or expired |
| 7 | Guarded execution failed |

Shell tokens and receipts are kept under `/data/adb/ashlooper/external-control` with owner-only permissions. Guidance outcomes also appear in the AshReXcue activity snapshot.
