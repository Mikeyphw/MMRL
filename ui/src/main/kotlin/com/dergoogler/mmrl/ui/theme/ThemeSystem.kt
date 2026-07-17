package com.dergoogler.mmrl.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.dergoogler.mmrl.ui.theme.color.AlmondBlossomDarkScheme
import com.dergoogler.mmrl.ui.theme.color.AlmondBlossomLightScheme
import com.dergoogler.mmrl.ui.theme.color.JeufosseDarkScheme
import com.dergoogler.mmrl.ui.theme.color.JeufosseLightScheme
import com.dergoogler.mmrl.ui.theme.color.MMRLBaseDarkScheme
import com.dergoogler.mmrl.ui.theme.color.MMRLBaseLightScheme
import com.dergoogler.mmrl.ui.theme.color.PlainAuversDarkScheme
import com.dergoogler.mmrl.ui.theme.color.PlainAuversLightScheme
import com.dergoogler.mmrl.ui.theme.color.PoppyFieldDarkScheme
import com.dergoogler.mmrl.ui.theme.color.PoppyFieldLightScheme
import com.dergoogler.mmrl.ui.theme.color.PourvilleDarkScheme
import com.dergoogler.mmrl.ui.theme.color.PourvilleLightScheme
import com.dergoogler.mmrl.ui.theme.color.SoleilLevantDarkScheme
import com.dergoogler.mmrl.ui.theme.color.SoleilLevantLightScheme
import com.dergoogler.mmrl.ui.theme.color.WildRosesDarkScheme
import com.dergoogler.mmrl.ui.theme.color.WildRosesLightScheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Serializable
enum class ThemeColorSource {
    BUILT_IN,
    DYNAMIC_ACCENT,
    DYNAMIC_FULL,
    CUSTOM,
}

@Serializable
enum class ThemeSurfaceStyle {
    FLAT,
    SOFT_TONAL,
    HIGH_CONTRAST,
}

@Serializable
enum class ThemeContrast {
    STANDARD,
    MEDIUM,
    HIGH,
}

@Serializable
data class CustomThemeDocument(
    val schema: Int = 1,
    val id: String = "custom.midnight",
    val name: String = "Midnight",
    val accent: String = "#86A9FF",
    val secondary: String = "#BE9BFF",
    val background: String = "#090B0E",
    val surface: String = "#12161B",
    val success: String = "#72D39A",
    val warning: String = "#F4BE62",
    val error: String = "#FF858B",
) {
    fun validate(): Result<CustomThemeDocument> = runCatching {
        require(schema == 1) { "Unsupported custom theme schema: $schema" }
        require(id.matches(Regex("[a-zA-Z0-9._-]{3,64}"))) { "Theme ID is invalid" }
        require(name.trim().length in 1..48) { "Theme name must be 1–48 characters" }
        listOf(accent, secondary, background, surface, success, warning, error).forEach(::parseHexColor)
        require(contrastRatio(parseHexColor(background), readableOn(parseHexColor(background))) >= 4.5) {
            "Background does not provide readable text contrast"
        }
        copy(name = name.trim())
    }

    fun encode(pretty: Boolean = true): String =
        (if (pretty) PRETTY_JSON else JSON).encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = false }
        private val PRETTY_JSON = Json { prettyPrint = true }

        fun decode(value: String): Result<CustomThemeDocument> = runCatching {
            JSON.decodeFromString(serializer(), value).validate().getOrThrow()
        }
    }
}

data class ThemeDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val previewColors: List<Color>,
    val legacyId: Int? = null,
)

data class SemanticColors(
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val updateAvailable: Color,
    val rebootRequired: Color,
    val verified: Color,
    val incompatible: Color,
    val disabled: Color,
    val rollbackAvailable: Color,
)

data class MMRLSurfaces(
    val background: Color,
    val navigation: Color,
    val row: Color,
    val rowHovered: Color,
    val sheet: Color,
    val dialog: Color,
    val input: Color,
    val selected: Color,
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = Color(0xFF72D39A),
        warning = Color(0xFFF4BE62),
        error = Color(0xFFFF858B),
        info = Color(0xFF79CDE8),
        updateAvailable = Color(0xFF8AADFF),
        rebootRequired = Color(0xFFF4BE62),
        verified = Color(0xFF79CDE8),
        incompatible = Color(0xFFFF858B),
        disabled = Color(0xFF8B949F),
        rollbackAvailable = Color(0xFFBE9BFF),
    )
}

val LocalMMRLSurfaces = staticCompositionLocalOf {
    MMRLSurfaces(
        background = Color(0xFF090B0E),
        navigation = Color(0xFF0F1216),
        row = Color.Transparent,
        rowHovered = Color(0xFF14181D),
        sheet = Color(0xFF0F1216),
        dialog = Color(0xFF12161B),
        input = Color(0xFF14181D),
        selected = Color(0xFF1A2B4B),
    )
}

data class ResolvedTheme(
    val colorScheme: ColorScheme,
    val semanticColors: SemanticColors,
    val surfaces: MMRLSurfaces,
    val palette: ThemeDefinition,
)

object ThemeRegistry {
    const val DEFAULT_ID = "mmrl"
    const val DRACULA_ID = "dracula"
    const val SWEET_DARK_ID = "sweet_dark"
    const val NORD_ID = "nord"
    const val MONOKAI_ID = "monokai"

    private fun palette(
        id: String,
        name: String,
        description: String,
        dark: ColorScheme,
        light: ColorScheme,
        legacyId: Int? = null,
    ) = ThemeDefinition(
        id = id,
        displayName = name,
        description = description,
        lightColorScheme = light,
        darkColorScheme = dark,
        previewColors = listOf(dark.primary, dark.secondary, dark.tertiary, dark.surfaceContainer),
        legacyId = legacyId,
    )

    private val draculaDark = darkScheme(
        primary = Color(0xFFBD93F9),
        secondary = Color(0xFFFF79C6),
        tertiary = Color(0xFF8BE9FD),
        background = Color(0xFF101116),
        surface = Color(0xFF171820),
        error = Color(0xFFFF6E7A),
    )
    private val sweetDark = darkScheme(
        primary = Color(0xFFFF7EB6),
        secondary = Color(0xFFCDA6FF),
        tertiary = Color(0xFF78DCE8),
        background = Color(0xFF100D11),
        surface = Color(0xFF191419),
        error = Color(0xFFFF8991),
    )
    private val nordDark = darkScheme(
        primary = Color(0xFF88C0D0),
        secondary = Color(0xFF81A1C1),
        tertiary = Color(0xFF8FBCBB),
        background = Color(0xFF0E1116),
        surface = Color(0xFF171C24),
        error = Color(0xFFBF616A),
    )
    private val monokaiDark = darkScheme(
        primary = Color(0xFFA6E22E),
        secondary = Color(0xFFF92672),
        tertiary = Color(0xFF66D9EF),
        background = Color(0xFF0F100D),
        surface = Color(0xFF191A16),
        error = Color(0xFFF45B69),
    )

    val builtIns: List<ThemeDefinition> = listOf(
        palette(DEFAULT_ID, "MMRL", "Neutral dark surfaces with restrained blue accents", MMRLBaseDarkScheme, MMRLBaseLightScheme, 7),
        palette(DRACULA_ID, "Dracula", "Purple, pink and cyan on a cool near-black base", draculaDark, lightFromDark(draculaDark)),
        palette(SWEET_DARK_ID, "Sweet Dark", "Warm charcoal surfaces with rose and violet accents", sweetDark, lightFromDark(sweetDark)),
        palette(NORD_ID, "Nord", "Muted frost blues with low visual noise", nordDark, lightFromDark(nordDark)),
        palette(MONOKAI_ID, "Monokai", "Warm black surfaces with vivid green and pink accents", monokaiDark, lightFromDark(monokaiDark)),
        palette("legacy_pourville", "Pourville", "Legacy MMRL palette", PourvilleDarkScheme, PourvilleLightScheme, 0),
        palette("legacy_soleil_levant", "Soleil Levant", "Legacy MMRL palette", SoleilLevantDarkScheme, SoleilLevantLightScheme, 1),
        palette("legacy_jeufosse", "Jeufosse", "Legacy MMRL palette", JeufosseDarkScheme, JeufosseLightScheme, 2),
        palette("legacy_poppy_field", "Poppy Field", "Legacy MMRL palette", PoppyFieldDarkScheme, PoppyFieldLightScheme, 3),
        palette("legacy_almond_blossom", "Almond Blossom", "Legacy MMRL palette", AlmondBlossomDarkScheme, AlmondBlossomLightScheme, 4),
        palette("legacy_plain_auvers", "Plain Auvers", "Legacy MMRL palette", PlainAuversDarkScheme, PlainAuversLightScheme, 5),
        palette("legacy_wild_roses", "Wild Roses", "Legacy MMRL palette", WildRosesDarkScheme, WildRosesLightScheme, 6),
    )

    fun byId(id: String): ThemeDefinition = builtIns.firstOrNull { it.id == id } ?: builtIns.first()

    fun migrateLegacyId(id: Int): String = when (id) {
        -1 -> DEFAULT_ID
        else -> builtIns.firstOrNull { it.legacyId == id }?.id ?: DEFAULT_ID
    }

    fun resolve(
        context: Context,
        darkMode: Boolean,
        paletteId: String,
        source: ThemeColorSource,
        dynamicFallbackPaletteId: String,
        surfaceStyle: ThemeSurfaceStyle,
        contrast: ThemeContrast,
        pureBlack: Boolean,
        accentIntensity: Float,
        enhancedStatusDistinction: Boolean,
        customThemeJson: String,
    ): ResolvedTheme {
        val requestedPalette = byId(paletteId)
        val fallbackPalette = byId(dynamicFallbackPaletteId)
        val palette = if (source == ThemeColorSource.CUSTOM) customDefinition(customThemeJson).getOrElse { requestedPalette } else requestedPalette
        val staticScheme = if (darkMode) palette.darkColorScheme else palette.lightColorScheme
        val base = when (source) {
            ThemeColorSource.BUILT_IN, ThemeColorSource.CUSTOM -> staticScheme
            ThemeColorSource.DYNAMIC_ACCENT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val dynamic = if (darkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    staticScheme.copy(
                        primary = dynamic.primary,
                        onPrimary = dynamic.onPrimary,
                        primaryContainer = dynamic.primaryContainer,
                        onPrimaryContainer = dynamic.onPrimaryContainer,
                        secondary = dynamic.secondary,
                        onSecondary = dynamic.onSecondary,
                        secondaryContainer = dynamic.secondaryContainer,
                        onSecondaryContainer = dynamic.onSecondaryContainer,
                        tertiary = dynamic.tertiary,
                        onTertiary = dynamic.onTertiary,
                        tertiaryContainer = dynamic.tertiaryContainer,
                        onTertiaryContainer = dynamic.onTertiaryContainer,
                        surfaceTint = dynamic.primary,
                    )
                } else {
                    if (darkMode) fallbackPalette.darkColorScheme else fallbackPalette.lightColorScheme
                }
            }
            ThemeColorSource.DYNAMIC_FULL -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (darkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (darkMode) fallbackPalette.darkColorScheme else fallbackPalette.lightColorScheme
                }
            }
        }
        val intensity = accentIntensity.coerceIn(0f, 1f)
        val accented = base.copy(
            primary = lerp(base.onSurfaceVariant, base.primary, 0.35f + intensity * 0.65f),
            secondary = lerp(base.onSurfaceVariant, base.secondary, 0.35f + intensity * 0.65f),
            tertiary = lerp(base.onSurfaceVariant, base.tertiary, 0.35f + intensity * 0.65f),
        )
        val styled = applySurfaceStyle(accented, surfaceStyle, darkMode)
        val contrasted = applyContrast(styled, contrast, darkMode)
        val finalScheme = if (pureBlack && darkMode) {
            contrasted.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceContainerLowest = Color.Black,
            )
        } else contrasted
        return ResolvedTheme(
            colorScheme = finalScheme,
            semanticColors = semanticColors(finalScheme, darkMode, enhancedStatusDistinction, customThemeJson.takeIf { source == ThemeColorSource.CUSTOM }, palette.id),
            surfaces = surfaces(finalScheme, surfaceStyle),
            palette = palette,
        )
    }

    fun customDefinition(json: String): Result<ThemeDefinition> = CustomThemeDocument.decode(json).map { doc ->
        val dark = customDarkScheme(doc)
        palette(
            id = doc.id,
            name = doc.name,
            description = "Imported custom palette",
            dark = dark,
            light = lightFromDark(dark),
        )
    }

    fun webCssVariables(resolved: ResolvedTheme): String = with(resolved) {
        val c = colorScheme
        val s = semanticColors
        """
        :root {
          --mmrl-background: ${c.background.css()};
          --mmrl-surface: ${c.surface.css()};
          --mmrl-surface-container: ${c.surfaceContainer.css()};
          --mmrl-primary: ${c.primary.css()};
          --mmrl-on-primary: ${c.onPrimary.css()};
          --mmrl-text: ${c.onSurface.css()};
          --mmrl-muted: ${c.onSurfaceVariant.css()};
          --mmrl-success: ${s.success.css()};
          --mmrl-warning: ${s.warning.css()};
          --mmrl-error: ${s.error.css()};
          --mmrl-info: ${s.info.css()};
          --mmrl-update: ${s.updateAvailable.css()};
          --mmrl-reboot: ${s.rebootRequired.css()};
          --mmrl-verified: ${s.verified.css()};
          --mmrl-incompatible: ${s.incompatible.css()};
          --mmrl-disabled: ${s.disabled.css()};
          --mmrl-rollback: ${s.rollbackAvailable.css()};
        }
        """.trimIndent()
    }
}

private fun customDarkScheme(doc: CustomThemeDocument): ColorScheme = darkScheme(
    primary = parseHexColor(doc.accent),
    secondary = parseHexColor(doc.secondary),
    tertiary = parseHexColor(doc.secondary),
    background = parseHexColor(doc.background),
    surface = parseHexColor(doc.surface),
    error = parseHexColor(doc.error),
)

private fun darkScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    surface: Color,
    error: Color,
): ColorScheme {
    val p = primary
    val s = secondary
    val t = tertiary
    val bg = background
    val sf = surface
    val err = error
    return darkColorScheme(
        primary = p,
        onPrimary = readableOn(p),
        primaryContainer = lerp(bg, p, .24f),
        onPrimaryContainer = readableOn(lerp(bg, p, .24f)),
        secondary = s,
        onSecondary = readableOn(s),
        secondaryContainer = lerp(bg, s, .20f),
        onSecondaryContainer = readableOn(lerp(bg, s, .20f)),
        tertiary = t,
        onTertiary = readableOn(t),
        tertiaryContainer = lerp(bg, t, .20f),
        onTertiaryContainer = readableOn(lerp(bg, t, .20f)),
        background = bg,
        onBackground = Color(0xFFF2F4F7),
        surface = bg,
        onSurface = Color(0xFFF2F4F7),
        surfaceVariant = lerp(bg, sf, .80f),
        onSurfaceVariant = Color(0xFFAAB2BD),
        surfaceTint = p,
        inverseSurface = Color(0xFFE9ECF0),
        inverseOnSurface = Color(0xFF171A1F),
        inversePrimary = lerp(p, Color.White, .25f),
        error = err,
        onError = readableOn(err),
        errorContainer = lerp(bg, err, .30f),
        onErrorContainer = Color(0xFFFFDADB),
        outline = lerp(sf, Color.White, .18f),
        outlineVariant = lerp(bg, sf, .70f),
        scrim = Color.Black,
        surfaceBright = lerp(bg, sf, .96f),
        surfaceDim = bg,
        surfaceContainerLowest = lerp(bg, Color.Black, .12f),
        surfaceContainerLow = lerp(bg, sf, .45f),
        surfaceContainer = sf,
        surfaceContainerHigh = lerp(sf, Color.White, .035f),
        surfaceContainerHighest = lerp(sf, Color.White, .07f),
    )
}

private fun lightFromDark(dark: ColorScheme): ColorScheme {
    val bg = Color(0xFFF7F8FA)
    val surface = Color.White
    return lightColorScheme(
        primary = dark.primary.copy(alpha = 1f),
        onPrimary = readableOn(dark.primary),
        primaryContainer = lerp(surface, dark.primary, .17f),
        onPrimaryContainer = readableOn(lerp(surface, dark.primary, .17f)),
        secondary = dark.secondary,
        onSecondary = readableOn(dark.secondary),
        secondaryContainer = lerp(surface, dark.secondary, .16f),
        onSecondaryContainer = readableOn(lerp(surface, dark.secondary, .16f)),
        tertiary = dark.tertiary,
        onTertiary = readableOn(dark.tertiary),
        tertiaryContainer = lerp(surface, dark.tertiary, .16f),
        onTertiaryContainer = readableOn(lerp(surface, dark.tertiary, .16f)),
        background = bg,
        onBackground = Color(0xFF16191E),
        surface = surface,
        onSurface = Color(0xFF16191E),
        surfaceVariant = Color(0xFFE6E9EE),
        onSurfaceVariant = Color(0xFF505966),
        surfaceTint = dark.primary,
        inverseSurface = Color(0xFF20242A),
        inverseOnSurface = Color(0xFFF1F3F6),
        inversePrimary = lerp(dark.primary, Color.White, .35f),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF747D89),
        outlineVariant = Color(0xFFD7DCE3),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFDDE1E7),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F3F6),
        surfaceContainer = Color(0xFFEBEEF2),
        surfaceContainerHigh = Color(0xFFE5E8ED),
        surfaceContainerHighest = Color(0xFFDDE1E7),
    )
}

private fun applySurfaceStyle(scheme: ColorScheme, style: ThemeSurfaceStyle, dark: Boolean): ColorScheme = when (style) {
    ThemeSurfaceStyle.FLAT -> scheme.copy(
        outline = Color.Transparent,
        outlineVariant = Color.Transparent,
        surfaceContainerLow = scheme.background,
        surfaceContainer = if (dark) lerp(scheme.background, scheme.onBackground, .045f) else scheme.surface,
        surfaceContainerHigh = if (dark) lerp(scheme.background, scheme.onBackground, .065f) else lerp(scheme.surface, scheme.onSurface, .035f),
        surfaceContainerHighest = if (dark) lerp(scheme.background, scheme.onBackground, .09f) else lerp(scheme.surface, scheme.onSurface, .055f),
    )
    ThemeSurfaceStyle.SOFT_TONAL -> scheme
    ThemeSurfaceStyle.HIGH_CONTRAST -> scheme.copy(
        outline = if (dark) Color(0xFF6F7885) else Color(0xFF5D6570),
        outlineVariant = if (dark) Color(0xFF454D58) else Color(0xFFB8BEC7),
        surfaceContainer = if (dark) lerp(scheme.background, Color.White, .09f) else lerp(scheme.surface, Color.Black, .06f),
        surfaceContainerHigh = if (dark) lerp(scheme.background, Color.White, .13f) else lerp(scheme.surface, Color.Black, .09f),
    )
}

private fun applyContrast(scheme: ColorScheme, contrast: ThemeContrast, dark: Boolean): ColorScheme = when (contrast) {
    ThemeContrast.STANDARD -> scheme
    ThemeContrast.MEDIUM -> scheme.copy(
        onBackground = if (dark) Color(0xFFF7F8FA) else Color(0xFF101216),
        onSurface = if (dark) Color(0xFFF7F8FA) else Color(0xFF101216),
        onSurfaceVariant = if (dark) Color(0xFFC3CAD3) else Color(0xFF414954),
    )
    ThemeContrast.HIGH -> scheme.copy(
        onBackground = if (dark) Color.White else Color.Black,
        onSurface = if (dark) Color.White else Color.Black,
        onSurfaceVariant = if (dark) Color(0xFFE0E5EB) else Color(0xFF22272E),
        outline = if (dark) Color(0xFFAAB2BD) else Color(0xFF454B54),
    )
}

private fun semanticColors(
    scheme: ColorScheme,
    dark: Boolean,
    enhanced: Boolean,
    customJson: String?,
    paletteId: String,
): SemanticColors {
    val custom = customJson?.let { CustomThemeDocument.decode(it).getOrNull() }
    val paletteSemantics = when (paletteId) {
        ThemeRegistry.DRACULA_ID -> listOf(Color(0xFF50FA7B), Color(0xFFFFB86C), Color(0xFFFF6E7A), Color(0xFF8BE9FD))
        ThemeRegistry.SWEET_DARK_ID -> listOf(Color(0xFF7DD3A8), Color(0xFFF5C06A), Color(0xFFFF8991), Color(0xFF78DCE8))
        ThemeRegistry.NORD_ID -> listOf(Color(0xFFA3BE8C), Color(0xFFEBCB8B), Color(0xFFBF616A), Color(0xFF88C0D0))
        ThemeRegistry.MONOKAI_ID -> listOf(Color(0xFFA6E22E), Color(0xFFE6DB74), Color(0xFFF45B69), Color(0xFF66D9EF))
        else -> listOf(Color(0xFF72D39A), Color(0xFFF4BE62), scheme.error, Color(0xFF79CDE8))
    }
    val success = custom?.let { parseHexColor(it.success) } ?: if (dark) paletteSemantics[0] else Color(0xFF187944)
    val warning = custom?.let { parseHexColor(it.warning) } ?: if (dark) paletteSemantics[1] else Color(0xFF8A5800)
    val error = custom?.let { parseHexColor(it.error) } ?: if (dark) paletteSemantics[2] else scheme.error
    val info = if (dark) paletteSemantics[3] else Color(0xFF15647C)
    return SemanticColors(
        success = success,
        warning = warning,
        error = error,
        info = info,
        updateAvailable = if (enhanced) Color(0xFF5AA7FF) else scheme.primary,
        rebootRequired = if (enhanced) Color(0xFFFFB84D) else warning,
        verified = if (enhanced) Color(0xFF45C9DF) else info,
        incompatible = if (enhanced) Color(0xFFFF6B75) else error,
        disabled = if (dark) Color(0xFF8B949F) else Color(0xFF626B76),
        rollbackAvailable = if (enhanced) Color(0xFFC69CFF) else scheme.secondary,
    )
}

private fun surfaces(scheme: ColorScheme, style: ThemeSurfaceStyle): MMRLSurfaces = MMRLSurfaces(
    background = scheme.background,
    navigation = scheme.surfaceContainerLow,
    row = if (style == ThemeSurfaceStyle.FLAT) Color.Transparent else scheme.surfaceContainerLow,
    rowHovered = scheme.surfaceContainer,
    sheet = scheme.surfaceContainerLow,
    dialog = scheme.surfaceContainer,
    input = scheme.surfaceContainer,
    selected = scheme.primaryContainer,
)

fun parseHexColor(value: String): Color {
    val clean = value.trim().removePrefix("#")
    require(clean.length == 6 || clean.length == 8) { "Expected #RRGGBB or #AARRGGBB" }
    val argb = if (clean.length == 6) "FF$clean" else clean
    return Color(argb.toLong(16))
}

private fun readableOn(color: Color): Color = if (color.luminanceCompat() > .45) Color(0xFF101216) else Color.White

private fun Color.luminanceCompat(): Double {
    fun linearize(component: Float): Double {
        val value = component.toDouble().coerceIn(0.0, 1.0)
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * linearize(red) +
        0.7152 * linearize(green) +
        0.0722 * linearize(blue)
}
private fun contrastRatio(a: Color, b: Color): Double {
    val l1 = a.luminanceCompat()
    val l2 = b.luminanceCompat()
    return (max(l1, l2) + .05) / (min(l1, l2) + .05)
}
private fun Color.css(): String = "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
