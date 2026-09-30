# MMRL repository wrapper

MMRL exposes its supported local automation through the generated `devtoolw` launcher. The launcher is intentionally thin and delegates to Devtool's repository wrapper contract in `.devtool.toml`, preserving canonical Devtool routing, EXO execution records, run-store evidence, and target identity.

```sh
./devtoolw --help
./devtoolw build
./devtoolw test
./devtoolw lint
./devtoolw validate
./devtoolw package
./devtoolw release
./devtoolw device
./devtoolw clean
```

`release` invokes the target-local `release-seal` workflow. `device` invokes the temporary `device-compat` workflow retained until MMRL-DT-X02 qualifies Devtool's native instrumentation path.

Inspect or verify the contract with:

```sh
devtool wrapper describe
devtool wrapper discover
devtool wrapper verify
devtool wrapper seal
```

For repository diagnosis, use `devtool --repo . --target mmrl_android doctor`; the current wrapper canonical-command registry does not expose `doctor`, and MMRL does not emulate it with a nested Devtool invocation.

Do not edit `devtoolw` or `devtoolw.cmd` by hand. Refresh Devtool-managed launchers with `devtool wrapper update` after upgrading Devtool.
