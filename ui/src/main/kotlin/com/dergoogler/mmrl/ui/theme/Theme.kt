package com.dergoogler.mmrl.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dergoogler.mmrl.ui.component.StatusBarStyle
import com.dergoogler.mmrl.ui.providable.LocalNavController
import com.dergoogler.mmrl.ui.token.LocalTypography

@Composable
fun MMRLAppTheme(
    paletteId: String = ThemeRegistry.DEFAULT_ID,
    colorSource: ThemeColorSource = ThemeColorSource.BUILT_IN,
    dynamicFallbackPaletteId: String = ThemeRegistry.NORD_ID,
    surfaceStyle: ThemeSurfaceStyle = ThemeSurfaceStyle.FLAT,
    contrast: ThemeContrast = ThemeContrast.STANDARD,
    pureBlack: Boolean = false,
    accentIntensity: Float = .72f,
    enhancedStatusDistinction: Boolean = true,
    customThemeJson: String = "",
    darkMode: Boolean = isSystemInDarkTheme(),
    navController: NavHostController = rememberNavController(),
    providerValues: Array<ProvidedValue<*>>? = null,
    context: Context = LocalContext.current,
    content: @Composable () -> Unit,
) {
    val resolved = ThemeRegistry.resolve(
        context = context,
        darkMode = darkMode,
        paletteId = paletteId,
        source = colorSource,
        dynamicFallbackPaletteId = dynamicFallbackPaletteId,
        surfaceStyle = surfaceStyle,
        contrast = contrast,
        pureBlack = pureBlack,
        accentIntensity = accentIntensity,
        enhancedStatusDistinction = enhancedStatusDistinction,
        customThemeJson = customThemeJson,
    )

    StatusBarStyle(darkMode = darkMode)

    val baseProviders: Array<ProvidedValue<*>> = arrayOf(
        LocalNavController provides navController,
        LocalTypography provides Typography,
        LocalSemanticColors provides resolved.semanticColors,
        LocalMMRLSurfaces provides resolved.surfaces,
    )

    val allProviders: Array<ProvidedValue<*>> = providerValues?.let { baseProviders + it } ?: baseProviders

    CompositionLocalProvider(*allProviders) {
        MaterialTheme(
            colorScheme = resolved.colorScheme,
            shapes = Shapes,
            typography = Typography,
            content = content,
        )
    }
}
