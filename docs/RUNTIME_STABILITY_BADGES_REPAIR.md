# Runtime stability and Activity badges

This pass keeps app behavior stable after the LSPosed integration stack by tightening build and Activity presentation seams.

## Moshi/KSP stability

Moshi model adapters continue to be generated through KSP. The Hilt Java aggregation task assembles its annotation processor path lazily on some AGP/Hilt variants, so the build script strips `moshi-kotlin-codegen-*` from that path in a `doFirst` action. This keeps Moshi off the legacy KAPT-compatible path while preserving the normal `ksp(libs.square.moshi.kotlin)` dependency.

## Activity badges

The Activity primary tab badges attention-worthy MMRL operations that are failed, pending reboot, or still running. Pending reboot state remains visible without coupling Activity to a separate recovery product.
