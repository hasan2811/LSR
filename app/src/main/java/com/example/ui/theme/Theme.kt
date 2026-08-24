package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = DarkBg,
    secondaryContainer = GeometricSecondaryContainer,
    onSecondaryContainer = GeometricOnSecondaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = GeometricPrimary,
    onPrimary = GeometricOnPrimary,
    background = GeometricBg,
    surface = GeometricOnPrimary,
    secondaryContainer = GeometricSecondaryContainer,
    onSecondaryContainer = GeometricOnSecondaryContainer,
    outline = GeometricOutline,
    outlineVariant = GeometricOutlineVariant,
    error = CriticalDot,
    errorContainer = CriticalBg,
    onErrorContainer = CriticalText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamic color to false by default to ensure our pristine geometric branding is preserved!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
