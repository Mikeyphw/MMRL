# MMRL Fork Theme System

## Fork identity

- Application ID: `com.mikeyphw.mmrl`
- Debug application ID: `com.mikeyphw.mmrl.debug`
- Kotlin/Android namespace remains `com.dergoogler.mmrl` to avoid a disruptive source migration.
- WebUI X compatibility permissions remain based on `com.dergoogler.mmrl` because those permissions are defined by the external WebUI companion package.
- Launcher name and APK output use **MMRL Fork**.
- Fork deep links support `mmrl-mikeyphw://repository` and no longer claim exclusive verification for the upstream `mmrl.dev` domain.

## Theme model

Theme settings are independent:

1. Appearance: follow system, light, dark, optional battery-saver dark override.
2. Color source: built-in palette, Wallpaper Accent, Full Monet, or custom JSON.
3. Palette: stable string IDs rather than positional integers.
4. Surface style: Flat, Soft Tonal, or High Contrast.
5. Contrast: Standard, Medium, or High.
6. OLED pure-black background.
7. Accent intensity.
8. Enhanced status distinction.

## Built-in palettes

- MMRL
- Dracula
- Sweet Dark
- Nord
- Monokai
- Existing art palettes retained under stable `legacy_*` IDs.

Legacy integer choices are read through `ThemeRegistry.migrateLegacyId`. The old dynamic ID (`-1`) resolves to Full Monet until the user saves a new stable theme choice.

## Monet

- **Wallpaper Accent** keeps the selected palette's neutral surfaces and applies dynamic colors to actions, switches, selections, and progress.
- **Full Monet** uses Android's full dynamic Material color scheme.
- Android versions before 12 use the configured fallback palette.

## Custom JSON

The settings screen accepts schema 1 documents through a validated editor or Android document picker, and exports them through both the clipboard and a JSON document.

```json
{
  "schema": 1,
  "id": "custom.midnight",
  "name": "Midnight",
  "accent": "#86A9FF",
  "secondary": "#BE9BFF",
  "background": "#090B0E",
  "surface": "#12161B",
  "success": "#72D39A",
  "warning": "#F4BE62",
  "error": "#FF858B"
}
```

IDs, names, schema versions, color syntax, and text contrast are validated before preview or persistence.

## Semantic roles

`LocalSemanticColors` provides stable roles for success, warning, error, information, update availability, reboot requirements, verification, incompatibility, disabled state, and rollback availability.

Repository rows, module details, update cards, and Activity history consume these roles instead of assigning arbitrary palette colors to operational states.

## Flat surfaces

`LocalMMRLSurfaces` provides explicit background, navigation, row, hover, sheet, dialog, input, and selected surfaces. Flat mode removes outlines and uses restrained tonal separation instead of borders or shadows.

## WebUI variables

Compatible WebUIs receive stable CSS aliases in `/internal/colors.css`, including:

- `--mmrl-background`
- `--mmrl-surface`
- `--mmrl-surface-container`
- `--mmrl-primary`
- `--mmrl-on-primary`
- `--mmrl-text`
- `--mmrl-muted`
- `--mmrl-success`
- `--mmrl-warning`
- `--mmrl-error`
- `--mmrl-info`
- `--mmrl-update`
- `--mmrl-reboot`
- `--mmrl-verified`
- `--mmrl-incompatible`
- `--mmrl-disabled`
- `--mmrl-rollback`
