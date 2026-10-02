# MMRL Devtool Android validation and release authority

MMRL-DT-X02 moves Android device execution and release qualification onto Devtool's canonical Android Operations services.

## Persistent validation

The existing `[validation.phases]` are now the repository's resumable validation contract. Use the wrapper-native validation command; Devtool preserves checkpoints and reuses successful phases when their relevant inputs did not change:

```sh
./devtoolw validate-session
./devtoolw validate-session --complete
./devtoolw validate-session --retry-failed --complete
# Less-common validation-control flags remain available after `--` or via Devtool directly.
devtool --repo . --target mmrl_android validate status
```

No phase inserts `clean`; Gradle/dependency caches and build outputs survive between checkpoints.

## Device authority

`[targets.mmrl_android.android.adb]` owns ADB provider and exactly-one-device policy. `[targets.mmrl_android.android.instrumentation]` owns the `officialDebug` instrumentation lane, cleanup, and logcat/screenshot-on-failure evidence. The EXO `device-native` job uses `runner = "android"` and `action = "instrumentation"`, so Gradle build work flows through the canonical planner and the device half flows through AOPS02/AOPS03.

`scripts/run-mmrl-device-validation.sh` is now only a compatibility CLI frontend. It contains no `adb` discovery and no `gradlew` call. `MMRL_ADB_SERIAL` is projected to `ANDROID_SERIAL` and Devtool remains responsible for device state/authorization.

## Release authority

The authoritative ship command is:

```sh
scripts/run-mmrl-release-seal.sh
# equivalently:
devtool --repo . --target mmrl_android release
```

Devtool AOPS04 activates `officialRelease`, runs release-lane unit/static contracts and strict release lint, packages the signed APK, requires canonical `officialDebug` instrumentation, verifies APK signatures and signer identity, performs install/upgrade smoke policy, and seals immutable SHA-256 release evidence into Devtool's normal artifact/run registry.

MMRL retains `signing.properties` as the credential authority (`mode = "project"`). Password values are not copied into `.devtool.toml`; Devtool owns the release-required policy and post-build identity verification.

G2 closes the X02 policy gap by making release instrumentation `required`, matching the already-required install-smoke device boundary. `build-release-apk.sh official release` remains the explicit package-only path: it disables device smoke and instrumentation while retaining signing/signature/certificate evidence. The full release now fails closed unless native instrumentation and install smoke both qualify.

## Wrapper boundary

Devtool wrapper schema 2 currently does not list `release` or `instrumentation` as wrapper-native commands. MMRL therefore does not create a recursive `devtoolw -> workflow -> devtool` bridge. `./devtoolw device` is safe because its EXO workflow lowers natively through the Android runner; the authoritative release command enters the first-class Devtool CLI directly.

## Durable validation authority

`./devtoolw validate-session --complete` binds the wrapper to Devtool's persistent sequential validation surface (`--sequential`). Phase state is durable and failures pause rather than forcing earlier successful Android phases to be repeated. The ordinary `./devtoolw validate` command remains available for one-shot or exact-task validation.

Native device/release Gradle work is bounded to one worker and a 768 MiB configured heap, including packaging, so moving these operations under AOPS does not silently expand MMRL's Termux memory envelope.
## Native validation ownership

`devtool validate` is reserved for Devtool's native Android resumable validation pipeline. The repository-owned EXO contract DAG is named `repository-check` and is exposed through `./devtoolw repository-check`; it must never be named `validate`, because target workflow selection takes precedence over the native runner action. Durable validation disables the separate dependency and instrumentation phases for this project: dependency coverage is transitively exercised by the owned Gradle phases, while the authoritative device/instrumentation run belongs to AOPS04 release qualification.

