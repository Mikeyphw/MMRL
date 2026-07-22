# Installed Modules version locks and snapshots

This overlay adds a review-first safety layer to the Installed Modules screen.

## Version policies

Each installed module can now have a local policy:

- Follow latest
- Ignore updates
- Lock the current version
- Allow updates only up to the current version code

The update checker still detects newer versions, but locked versions are shown as quiet metadata on the module card instead of becoming normal update prompts.

## Snapshots

Snapshots are metadata-only by default. They store module identity, version, versionCode, enabled state, description, author, Ash trust state, and local version policy. They do not blindly restore files.

The snapshot dialog builds a review-first plan with:

- Current
- Version changed
- State changed
- Missing
- Extra

Full folder or cached ZIP rollback can be layered on later, but the current behavior is intentionally safe for boot-sensitive modules.

## Installed Modules UI

The module list now favors scanning and inspection:

- A summary card exposes root provider, enabled/installed counts, update count, locked count, and snapshot count.
- Descriptions are always visible on module cards.
- Version, author, and module ID are always shown in a compact metadata line.
- Update, policy, WebUI, Action, AshReXcue, and runtime state badges are displayed as a horizontal strip to avoid smashed rows.
- Detailed metadata is available from the card or overflow menu.
