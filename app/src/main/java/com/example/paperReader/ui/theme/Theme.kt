package com.example.paperReader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LatticeLightColorScheme = lightColorScheme(
    primary = LatticeLightPrimary,
    onPrimary = LatticeLightOnPrimary,
    primaryContainer = LatticeLightPrimaryContainer,
    onPrimaryContainer = LatticeLightOnPrimaryContainer,
    secondary = LatticeLightSecondary,
    onSecondary = LatticeLightOnSecondary,
    secondaryContainer = LatticeLightSecondaryContainer,
    onSecondaryContainer = LatticeLightOnSecondaryContainer,
    background = LatticeLightBackground,
    onBackground = LatticeLightOnBackground,
    surface = LatticeLightSurface,
    onSurface = LatticeLightOnSurface,
    surfaceVariant = LatticeLightSurfaceVariant,
    onSurfaceVariant = LatticeLightOnSurfaceVariant,
    outline = LatticeLightOutline,
    outlineVariant = LatticeLightOutlineVariant,
)

private val LatticeDarkColorScheme = darkColorScheme(
    primary = LatticeDarkPrimary,
    onPrimary = LatticeDarkOnPrimary,
    primaryContainer = LatticeDarkPrimaryContainer,
    onPrimaryContainer = LatticeDarkOnPrimaryContainer,
    secondary = LatticeDarkSecondary,
    onSecondary = LatticeDarkOnSecondary,
    secondaryContainer = LatticeDarkSecondaryContainer,
    onSecondaryContainer = LatticeDarkOnSecondaryContainer,
    background = LatticeDarkBackground,
    onBackground = LatticeDarkOnBackground,
    surface = LatticeDarkSurface,
    onSurface = LatticeDarkOnSurface,
    surfaceVariant = LatticeDarkSurfaceVariant,
    onSurfaceVariant = LatticeDarkOnSurfaceVariant,
    outline = LatticeDarkOutline,
    outlineVariant = LatticeDarkOutlineVariant,
)

private val PaperReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class PaperReaderSpacing(
    val page: Dp = 20.dp,
    val section: Dp = 24.dp,
    val card: Dp = 16.dp,
    val compact: Dp = 12.dp,
    val control: Dp = 8.dp,
    val hairline: Dp = 1.dp,
)

val LocalPaperReaderSpacing = staticCompositionLocalOf { PaperReaderSpacing() }

val MaterialTheme.spacing: PaperReaderSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalPaperReaderSpacing.current

@Composable
fun PaperReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) LatticeDarkColorScheme else LatticeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PaperReaderTypography,
        shapes = PaperReaderShapes,
    ) {
        CompositionLocalProvider(
            LocalPaperReaderSpacing provides PaperReaderSpacing(),
            content = content,
        )
    }
}
