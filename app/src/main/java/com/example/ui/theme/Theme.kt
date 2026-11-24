package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TurquoisePrimary,
    onPrimary = TurquoiseOnPrimary,
    primaryContainer = TurquoiseContainer,
    onPrimaryContainer = TurquoiseOnContainer,
    secondary = SaffronSecondary,
    onSecondary = SaffronOnSecondary,
    secondaryContainer = SaffronContainer,
    onSecondaryContainer = SaffronOnContainer,
    tertiary = CoralTertiary,
    onTertiary = CoralOnTertiary,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = CoralOnContainer,
    background = MidnightBg,
    onBackground = LightTextPrimary,
    surface = StudioSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerHigh = StudioSurfaceElevated,
    outline = StudioOutline
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = DaylightSurface,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = DaylightSurface,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = RoseTertiaryLight,
    onTertiary = DaylightSurface,
    background = DaylightBg,
    onBackground = DarkTextPrimary,
    surface = DaylightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DaylightOutline
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AvaCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
