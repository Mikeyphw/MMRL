# Runtime stability, AshReXcue repair, and Activity badges

This pass keeps the app behavior stable after the LSPosed integration stack by tightening three runtime seams.

## Moshi code generation

Moshi model adapters continue to be generated through KSP. The Hilt Java aggregation task assembles its annotation processor path lazily on some AGP/Hilt variants, so the build script now strips `moshi-kotlin-codegen-*` from that path in a `doFirst` action. This keeps Moshi off the legacy KAPT-compatible path while preserving the normal `ksp(libs.square.moshi.kotlin)` dependency.

## AshReXcue bundled jq self-repair

The Ash root service now passes application context to the control executor. When an active AshReXcue install is detected but `/data/adb/modules/<Ash folder>/jq/jq` is missing or non-executable, the executor attempts to restore `jq/jq` from the bundled `AshReXcue_Bootloop_Protector.zip` asset and applies executable permissions before running live commands.

AshReXcue module detection also accepts legacy/renamed folders such as `AshReXcue_Bootloop_Protector`, even when metadata is incomplete. This prevents a present but partially damaged bundled install from being presented as simply missing.

## Activity badges

The Activity primary tab now badges attention-worthy work, not only pending reboot items. The badge count is derived from local and AshReXcue activity entries that are failed, pending reboot, or still running. The Recovery tab still badges pending reboot items so recovery-sensitive state remains visible.

## AshReXcue identity aliases

Root module surfaces now recognize AshReXcue aliases with separator-insensitive matching for special-case UI behavior. This covers `AshLooper`, `AshReXcue`, and `AshReXcue_Bootloop_Protector` without changing general module identity matching.
