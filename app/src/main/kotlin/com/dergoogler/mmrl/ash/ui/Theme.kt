package com.dergoogler.mmrl.ash.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dergoogler.mmrl.ash.model.ThemePreset

private fun palette(
    primary: Long,
    secondary: Long,
    tertiary: Long,
    background: Long,
    surface: Long,
    surfaceVariant: Long,
    outline: Long,
    error: Long,
): ColorScheme = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(0xFF07111A),
    primaryContainer = Color(primary).copy(alpha = 0.18f),
    onPrimaryContainer = Color(primary),
    secondary = Color(secondary),
    onSecondary = Color(0xFF08100D),
    secondaryContainer = Color(secondary).copy(alpha = 0.15f),
    onSecondaryContainer = Color(secondary),
    tertiary = Color(tertiary),
    background = Color(background),
    onBackground = Color(0xFFE5E9F0),
    surface = Color(surface),
    onSurface = Color(0xFFE5E9F0),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(0xFFB8C0CC),
    outline = Color(outline),
    error = Color(error),
    onError = Color(0xFF1A0508),
)

private val Mmrl = palette(0xFF9CCAFF, 0xFF8EE3C8, 0xFFD1B3FF, 0xFF0D1117, 0xFF121821, 0xFF1A222E, 0xFF3B4655, 0xFFFF8A9B)
private val Dracula = palette(0xFFBD93F9, 0xFF50FA7B, 0xFFFFB86C, 0xFF191A21, 0xFF21222C, 0xFF2A2C38, 0xFF4C4F62, 0xFFFF5555)
private val Nord = palette(0xFF88C0D0, 0xFFA3BE8C, 0xFFB48EAD, 0xFF242933, 0xFF2E3440, 0xFF3B4252, 0xFF4C566A, 0xFFBF616A)
private val Monokai = palette(0xFFA6E22E, 0xFF66D9EF, 0xFFAE81FF, 0xFF171812, 0xFF20211B, 0xFF2B2C24, 0xFF55564D, 0xFFF92672)

@Composable
fun AshReXcueTheme(preset: ThemePreset, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when (preset) {
        ThemePreset.MMRL -> Mmrl
        ThemePreset.Dracula -> Dracula
        ThemePreset.Nord -> Nord
        ThemePreset.Monokai -> Monokai
        ThemePreset.Monet -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            Mmrl
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
