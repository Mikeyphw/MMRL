# MMRL module platform convergence

This document describes the authoritative module-source behavior after consolidated OV03.

## LSPosed repository metadata

The LSPosed repository is backed by repositories in `Xposed-Modules-Repo`.
The upstream contract assigns these meanings:

- repository name: Android package name;
- GitHub repository description / API `description`: human-readable module title;
- `SUMMARY` / API `summary`: short module description shown in repository listings;
- `README.md`: long-form description and fallback descriptive content.

MMRL therefore renders `description` as the tile title and `summary` as the tile description.
This presentation mapping is shared by the standalone LSPosed repository UI and the Unified Module Browser.

## Fail-closed source generations

A newly downloaded LSPosed index is parsed and validated before replacing the last known-good cache.
If refresh fails or the remote body is malformed, MMRL may continue from a valid stale cache and exposes
that state as stale/partial instead of publishing the invalid generation.

Generic repository replacement remains generation-based: a repository is replaced only after source
loading and ingestion validation complete successfully.

## GitHub releases and nightly artifacts

Saved release rules are evaluated across eligible releases in repository order. A newest release whose
assets do not match the saved rule no longer prevents MMRL from selecting the next eligible release.
Nightly sources continue to select the newest successful run containing a matching, non-expired artifact.

A failed GitHub file transfer removes the partial destination so a later operation cannot mistake stale
partial bytes for the current successful download. Archive materialization remains bounded by entry and
uncompressed-byte limits.

## Background refresh completeness

Repository and module-update workers treat per-source failures as a partial generation and use bounded
WorkManager retries. During a partial module-update refresh, newly observed update notification keys may
be added but previously known keys are retained because their source may simply have failed in the current
attempt. A complete generation may retire stale keys.

## Unified browser

The canonical browser keeps repository, GitHub, root-module, LSPosed repository, installed LSPosed APK,
and local-source identities merged while preserving source provenance, version state, provider state,
scope state, version locks, and problem badges.
