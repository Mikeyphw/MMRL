# MMRL repository wrapper

MMRL exposes ordinary repository automation through the generated `devtoolw` launcher. The launcher delegates to Devtool's repository wrapper contract in `.devtool.toml`, preserving canonical routing, EXO execution records, run-store evidence, and target identity.

```sh
./devtoolw --help
./devtoolw build
./devtoolw test
./devtoolw lint
./devtoolw validate
./devtoolw validate-session --complete
./devtoolw package
./devtoolw release-check
./devtoolw device
./devtoolw clean
```

`validate-session` is the durable sequential validation entry point; it defaults to `--sequential --failure-action pause` and exposes common completion/retry/runtime controls. `release-check` is the repository-owned EXO pre-release DAG. `device` uses the native Android runner/instrumentation action and is no longer a raw-Gradle compatibility workflow.

The authoritative release command is `devtool --repo . --target mmrl_android release` (or `scripts/run-mmrl-release-seal.sh`). Current wrapper schema 2 does not expose `release` or `instrumentation` as wrapper-native actions; MMRL deliberately avoids nested Devtool recursion to fake that capability.

Inspect or verify the wrapper contract with `devtool wrapper describe`, `devtool wrapper discover`, `devtool wrapper verify`, and `devtool wrapper seal`. For repository diagnosis use `devtool --repo . --target mmrl_android doctor`.

Do not edit `devtoolw` or `devtoolw.cmd` by hand. Refresh Devtool-managed launchers with `devtool wrapper update` after upgrading Devtool.
