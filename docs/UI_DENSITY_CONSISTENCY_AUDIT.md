# UI density and consistency audit pass

This overlay applies a density and hierarchy pass after the module-governor and Activity-primary navigation work.

## Changes

- Recovery task chips use short labels on compact screens and the lifecycle banner uses tighter spacing so task content starts sooner.
- Installed module cards keep descriptions and direct WebUI/Action buttons visible, but the status strip no longer repeats WebUI/Action as duplicate pills.
- Module summary keeps status metrics only; snapshot actions remain available from per-module overflow/snapshot dialogs instead of consuming top-of-list height.
- The visible Home toolbar button has tighter padding so it competes less with screen titles and actions.
- Repository tabs are padded below the top app bar and repository/GitHub source strings are moved into resources.
- Shared status pills no longer expose themselves as image roles to accessibility services.

## Intent

The pass does not add new module-management features. It reduces visual competition between existing features so module state, safety, and available actions are easier to scan on phones.
