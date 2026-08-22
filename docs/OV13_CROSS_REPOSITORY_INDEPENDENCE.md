# OV13 — Cross-repository independence seal

MMRL is a standalone module manager. It has no AshReXcue client, Binder/Intent integration, package query, source dependency, Gradle project dependency, or special-case runtime behavior.

`verifyOv13CrossRepositoryIndependence` depends on the OV09/OV10 purge and cleanup gates, then additionally rejects:

- AshReXcue package names, protocols, module IDs, module payload names, or legacy embedded source packages from production source/resources.
- an `:ashrexcue` Gradle project, included build, sibling checkout path, or application dependency.
- AshReXcue manifest queries/components.
- source-controlled symlinks escaping the MMRL repository.

The gate explicitly re-verifies that MMRL's unrelated `ModuleSnapshot`, `ModuleSnapshotPlanner`, and snapshot persistence remain present.

The paired AshReXcue OV13 gate permits only the two historical MMRL package IDs inside its one-time OV12 legacy migration contract. MMRL itself has no reciprocal knowledge of AshReXcue.
