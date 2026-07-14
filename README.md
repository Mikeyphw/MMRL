# MMRL source build fixes

- Removes `@Destination<RootGraph>` from `MainScreen`, which is the application navigation host invoked directly by `MainActivity`, not a navigable destination. This prevents Compose Destinations KSP from treating callback lambdas as navigation arguments.
- Escapes the apostrophe in `settings_tasker_module_actions_desc` for AAPT2.

Apply over the MMRL repository and rebuild.

- Pins the native build to ARM64-host-capable NDK r29 (`29.0.14206865`). Install the linux-aarch64 termux-ndk r29 release in the SDK NDK directory before rebuilding.
