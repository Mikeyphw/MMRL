# LSPosed provider and Vector framework support

MMRL treats LSPosed repository entries as APK modules, but the framework that runs them can be installed as a root module. Vector is one such provider: its module ZIP declares `id=zygisk_vector`, bundles `manager.apk`, `daemon.apk`, `framework/lspd.dex`, and exposes `action.sh` as the manager launch bridge.

## Detection contract

The LSPosed screens now consider a provider available when either:

- a known LSPosed manager package has a normal launch intent, or
- an active root framework module exposes an `action.sh` launch bridge.

Recognized provider module IDs include:

- `zygisk_vector`
- `zygisk_lsposed`
- `riru_lsposed`
- `lsposed`

MMRL also accepts provider-looking root modules when their `module.prop` metadata and module files show an LSPosed/Vector framework shape, such as `manager.apk` or `framework/lspd.dex`.

## Open behavior

Open LSPosed prefers a normal Android launch intent. If the manager app is hidden or not installed as a normal launchable package, MMRL falls back to running the provider root module's action from the existing module action terminal flow. This keeps Vector's `action.sh` path available without pretending that the provider ZIP is an LSPosed APK module.

## Safety boundary

This does not change the APK-module safety boundary. MMRL can install LSPosed APK modules and open the provider manager, but enablement and scope selection still belong inside LSPosed/Vector.
