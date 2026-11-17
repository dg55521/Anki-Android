package com.example.anki.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.anki.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = AnkiBluePrimary,
    onPrimary = AnkiBlueOnPrimary,
    primaryContainer = AnkiBluePrimaryContainer,
    onPrimaryContainer = AnkiBlueOnPrimaryContainer,
    secondary = AnkiCyanSecondary,
    onSecondary = AnkiCyanOnSecondary,
    secondaryContainer = AnkiCyanSecondaryContainer,
    onSecondaryContainer = AnkiCyanOnSecondaryContainer,
    tertiary = AnkiAmberTertiary,
    onTertiary = AnkiAmberOnTertiary,
    tertiaryContainer = AnkiAmberTertiaryContainer,
    onTertiaryContainer = AnkiAmberOnTertiaryContainer,
    background = AnkiLightBackground,
    onBackground = AnkiLightOnSurface,
    surface = AnkiLightSurface,
    onSurface = AnkiLightOnSurface,
    surfaceVariant = AnkiLightSurfaceVariant,
    onSurfaceVariant = AnkiLightOnSurfaceVariant,
    outline = AnkiLightOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = AnkiDarkPrimary,
    onPrimary = AnkiDarkOnPrimary,
    primaryContainer = AnkiDarkPrimaryContainer,
    onPrimaryContainer = AnkiDarkOnPrimaryContainer,
    secondary = AnkiDarkSecondary,
    onSecondary = AnkiDarkOnSecondary,
    secondaryContainer = AnkiDarkSecondaryContainer,
    onSecondaryContainer = AnkiDarkOnSecondaryContainer,
    tertiary = AnkiDarkTertiary,
    onTertiary = AnkiDarkOnTertiary,
    tertiaryContainer = AnkiDarkTertiaryContainer,
    onTertiaryContainer = AnkiDarkOnTertiaryContainer,
    background = AnkiDarkBackground,
    onBackground = AnkiDarkOnSurface,
    surface = AnkiDarkSurface,
    onSurface = AnkiDarkOnSurface,
    surfaceVariant = AnkiDarkSurfaceVariant,
    onSurfaceVariant = AnkiDarkOnSurfaceVariant,
    outline = AnkiDarkOutline,
)

val AnkiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun AnkiDroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

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
        typography = AnkiTypography,
        shapes = AnkiShapes,
        content = content,
    )
}
