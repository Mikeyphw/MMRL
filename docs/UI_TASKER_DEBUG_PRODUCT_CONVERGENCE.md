# MMRL UI, Tasker, and Debug Workbench convergence

OV04 makes the feature-rich personal-use branch present one coherent product surface.

## Adaptive navigation

Compact layouts expose exactly four primary destinations — Home, Repos, Modules, and Activity — plus a More item. SuperUser, Settings, and Debug Workbench remain reachable from More without forcing six equal-width labeled bottom-bar items. Navigation labels are one line and badge counts are bounded to `99+` so a large update/failure count cannot distort item geometry. Medium and expanded layouts continue to use rail/drawer navigation.

## Activity

Activity remains the canonical operation history and review surface. A new Attention filter uses the same predicate as the navigation badge: active work (including approval waits), failed/unknown outcomes, and pending-reboot records. Downloads, failures, running work, and pending reboot retain their dedicated filters.

## Tasker

Tasker Settings surfaces the stable public output contract version/schema and links directly to Activity for approval, failure, retry, and result review. Mutating actions remain capability-gated and approval-aware. MMRL does not expose arbitrary root shell execution.

## Debug Workbench

Debug Workbench is a first-class diagnostic surface reachable from More as well as Developer settings. Probe sessions remain explicitly read-only. Mutating support actions are separate explicit button presses; merely opening the screen or running probes does not change module, repository, token, scope, or root state. Support history and exports remain redacted.

## Personal-use policy

The app remains a personal-use build. OV04 does not restore a Play Store flavor, Google-Play BuildConfig gate, or store-distribution branch.
