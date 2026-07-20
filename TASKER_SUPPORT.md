# MMRL Tasker integration

MMRL exposes module queries, update checks, queued downloads, controlled module operations, and reviewed installs through Tasker's plugin API. Every mutating request is routed through MMRL's existing root backend, Activity history, approval policy, archive inspection, and rollback system.

## Read-only and queue actions

### Get module status
Input: module ID. Returns installed/enabled state, installed and available versions, update state, source repository, pending-reboot state, and JSON.

### List installed modules
Returns module IDs, names, versions, states, count, and JSON. Installed modules are refreshed from the active root manager when possible.

### Check module updates
Uses cached repository data or forces a repository refresh. Per-module ignored updates are respected and the check is recorded in Activity.

### Get operation result
Input: MMRL operation ID. Returns operation type, status, phase, progress, errors, reboot state, rollback availability, and JSON.

### Download module
Inputs: repository module ID or direct HTTP/HTTPS ZIP URL, plus an optional filename. Downloads use MMRL's atomic two-slot queue and immediately return an operation ID.

### Cancel download
Input: running download operation ID.

### Export technical log
Input: operation ID. Returns a temporary read-only `content://` URI in `%mmrl_log_uri`; cache exports expire after 24 hours.

## Controlled root actions

### Enable module / Disable module
Input: installed module ID. The request is checked against the Tasker capability and approval policy, then queued asynchronously. The result reports an Activity operation ID and whether approval is pending.

### Remove module
Input: installed module ID. Removal has a separate capability toggle and follows the same approval pipeline.

### Run module action
Input: installed module ID. Only the module's bundled `action.sh` or the root manager's predefined action command can run. MMRL never accepts an arbitrary shell command from Tasker.

### Restore previous version
Input: Activity operation ID containing a retained rollback archive. Restoration is recorded as a child rollback operation.

## Reviewed install and update flow

### Prepare reviewed install
Provide either:
- a repository module ID, or
- a successful MMRL download operation ID.

MMRL obtains the archive, computes SHA-256, inspects scripts, native binaries, APKs, SELinux policy, system properties, remote execution references, traversal, and ZIP-bomb indicators. A successful review returns:

- `%mmrl_review_token`
- `%mmrl_review_expires_at`
- `%mmrl_safety_level`
- `%mmrl_inspection_summary`

Tokens expire after 30 minutes, are bound to the archive hash and module ID, and can be claimed by only one execution operation.

### Execute reviewed install
Input: review token. Before execution MMRL:

1. Revalidates token expiration and ownership.
2. Rehashes and reinspects the archive.
3. Enforces the configured Tasker capability and approval policy.
4. Always requires explicit approval for non-routine archives, even when the device is unlocked or the module is allowlisted.
5. Attempts a previous-version backup for updates.
6. Executes through the normal root backend.
7. Automatically restores the previous version after a failed update when possible.
8. Reports actual rollback state through Activity and Get operation result.

A review is considered routine only when the source is verified and the archive adds no boot scripts, APKs, SELinux policy, property changes, or remote-execution behavior.

## Approval settings

Open **Settings → Tasker** to configure:

- Tasker integration
- Downloads
- Enable/disable operations
- Predefined module actions
- Remove/restore operations
- Reviewed installs and updates
- Approval policy
- Preapproved module IDs

Approval policies:

- **Always ask**
- **Allow while device is unlocked**
- **Allow preapproved modules**
- **Never allow privileged automation**

Requests awaiting approval appear both as a high-priority notification and in Activity, so they remain actionable when notification permission is unavailable.

## Events

### Update discovered
Triggered when MMRL discovers a new tracked version. Ignored updates do not trigger it.

### Operation failed
Triggered when a persisted operation fails, including Tasker-controlled root actions and reviewed installs.

## Main output variables

- `%mmrl_success`
- `%mmrl_status`
- `%mmrl_message`
- `%mmrl_operation_id`
- `%mmrl_operation_type`
- `%mmrl_phase`
- `%mmrl_progress`
- `%mmrl_module_id`
- `%mmrl_module_name`
- `%mmrl_installed`
- `%mmrl_enabled`
- `%mmrl_installed_version`
- `%mmrl_available_version`
- `%mmrl_update_available`
- `%mmrl_update_ignored`
- `%mmrl_repository`
- `%mmrl_reboot_required`
- `%mmrl_rollback_available`
- `%mmrl_error_code`
- `%mmrl_error_message`
- `%mmrl_log_uri`
- `%mmrl_review_token`
- `%mmrl_review_expires_at`
- `%mmrl_approval_required`
- `%mmrl_safety_level`
- `%mmrl_inspection_summary`
- `%mmrl_result_json`
- `%mmrl_count`
- `%mmrl_module_ids()`
- `%mmrl_module_names()`
- `%mmrl_versions()`
- `%mmrl_states()`

## Safety boundary

MMRL does not expose generic root shell execution. Root changes are asynchronous, capability-gated, approval-aware, and persisted in Activity. Reviewed installs are hash-bound, revalidated immediately before execution, and use the same backup and rollback infrastructure as the normal app flow.

## AshReXcue recovery automation

Phase H adds typed AshReXcue Tasker actions for capabilities, recovery status, module evidence, guarded plan preview/execution, guidance outcomes, and evidence refresh. Mutating actions require the dedicated AshReXcue recovery capability switch. Plans are revision-bound, idempotent, rate limited, auditable, and executed through one-shot 30-minute tokens. High-risk plans always wait for MMRL approval, and no action exposes arbitrary shell execution. See `docs/ASHREXCUE_PHASE_H.md` for the JSON contract and shell equivalents.
