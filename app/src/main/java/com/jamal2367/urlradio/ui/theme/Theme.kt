/*
 * Theme.kt
 * The Material 3 Expressive theme for URL Radio
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * The brand fallback schemes. Only the roles the app actually had values for are
 * overridden; everything else keeps the Material 3 default.
 */
private val LightColors: ColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandPrimaryContainerLight,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
    secondary = BrandSecondaryLight,
    secondaryContainer = BrandSecondaryContainerLight,
    onSecondaryContainer = BrandOnSecondaryContainerLight,
    surface = BrandSurfaceLight,
    onSurface = BrandOnSurfaceLight,
    surfaceVariant = BrandSurfaceVariantLight,
    onSurfaceVariant = BrandOnSurfaceVariantLight,
    outline = BrandOutlineLight,
    error = BrandErrorLight,
    onError = BrandOnErrorLight,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = BrandSecondaryDark,
    secondaryContainer = BrandSecondaryContainerDark,
    onSecondaryContainer = BrandOnSecondaryContainerDark,
    surface = BrandSurfaceDark,
    onSurface = BrandOnSurfaceDark,
    surfaceVariant = BrandSurfaceVariantDark,
    onSurfaceVariant = BrandOnSurfaceVariantDark,
    outline = BrandOutlineDark,
    error = BrandErrorDark,
    onError = BrandOnErrorDark,
)

/**
 * The app theme.
 *
 * Dynamic colour is used from API 31 upwards, which mirrors what the app already did
 * through its res/values-v31 resources. Below API 31 the brand palette above is used.
 *
 * @param darkTheme whether to render the dark scheme. Callers pass the resolved value
 *   of the user's theme preference; the default follows the system.
 * @param dynamicColor set to false to force the brand palette even on API 31+.
 */
@Composable
fun UrlRadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = remember(darkTheme, dynamicColor, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColors
            else -> LightColors
        }
    }

    /*
     * Match the system bar icons to the scheme actually being drawn.
     *
     * enableEdgeToEdge() and the values/values-night XML themes both pick their icon
     * appearance from the *system* night mode, not from the app's own theme setting. With
     * the app forced to light while the system is dark, the status bar kept drawing white
     * icons onto the light background and they disappeared.
     */
    val view = LocalView.current
    val activity = LocalActivity.current
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
