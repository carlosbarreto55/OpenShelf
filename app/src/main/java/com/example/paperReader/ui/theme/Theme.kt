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
    inversePrimary = LatticeLightInversePrimary,
    secondary = LatticeLightSecondary,
    onSecondary = LatticeLightOnSecondary,
    secondaryContainer = LatticeLightSecondaryContainer,
    onSecondaryContainer = LatticeLightOnSecondaryContainer,
    tertiary = LatticeLightTertiary,
    onTertiary = LatticeLightOnTertiary,
    tertiaryContainer = LatticeLightTertiaryContainer,
    onTertiaryContainer = LatticeLightOnTertiaryContainer,
    error = LatticeLightError,
    onError = LatticeLightOnError,
    errorContainer = LatticeLightErrorContainer,
    onErrorContainer = LatticeLightOnErrorContainer,
    background = LatticeLightBackground,
    onBackground = LatticeLightOnBackground,
    surface = LatticeLightSurface,
    onSurface = LatticeLightOnSurface,
    surfaceContainerLowest = LatticeLightSurfaceContainerLowest,
    surfaceContainerLow = LatticeLightSurfaceContainerLow,
    surfaceContainer = LatticeLightSurfaceContainer,
    surfaceContainerHigh = LatticeLightSurfaceContainerHigh,
    surfaceContainerHighest = LatticeLightSurfaceContainerHighest,
    surfaceVariant = LatticeLightSurfaceVariant,
    onSurfaceVariant = LatticeLightOnSurfaceVariant,
    surfaceTint = LatticeLightPrimary,
    inverseSurface = LatticeLightInverseSurface,
    inverseOnSurface = LatticeLightInverseOnSurface,
    outline = LatticeLightOutline,
    outlineVariant = LatticeLightOutlineVariant,
    scrim = LatticeLightScrim,
)

private val LatticeDarkColorScheme = darkColorScheme(
    primary = LatticeDarkPrimary,
    onPrimary = LatticeDarkOnPrimary,
    primaryContainer = LatticeDarkPrimaryContainer,
    onPrimaryContainer = LatticeDarkOnPrimaryContainer,
    inversePrimary = LatticeDarkInversePrimary,
    secondary = LatticeDarkSecondary,
    onSecondary = LatticeDarkOnSecondary,
    secondaryContainer = LatticeDarkSecondaryContainer,
    onSecondaryContainer = LatticeDarkOnSecondaryContainer,
    tertiary = LatticeDarkTertiary,
    onTertiary = LatticeDarkOnTertiary,
    tertiaryContainer = LatticeDarkTertiaryContainer,
    onTertiaryContainer = LatticeDarkOnTertiaryContainer,
    error = LatticeDarkError,
    onError = LatticeDarkOnError,
    errorContainer = LatticeDarkErrorContainer,
    onErrorContainer = LatticeDarkOnErrorContainer,
    background = LatticeDarkBackground,
    onBackground = LatticeDarkOnBackground,
    surface = LatticeDarkSurface,
    onSurface = LatticeDarkOnSurface,
    surfaceContainerLowest = LatticeDarkSurfaceContainerLowest,
    surfaceContainerLow = LatticeDarkSurfaceContainerLow,
    surfaceContainer = LatticeDarkSurfaceContainer,
    surfaceContainerHigh = LatticeDarkSurfaceContainerHigh,
    surfaceContainerHighest = LatticeDarkSurfaceContainerHighest,
    surfaceVariant = LatticeDarkSurfaceVariant,
    onSurfaceVariant = LatticeDarkOnSurfaceVariant,
    // Flat surfaces: the reference uses tonal steps, not elevation overlays.
    surfaceTint = LatticeDarkSurface,
    inverseSurface = LatticeDarkInverseSurface,
    inverseOnSurface = LatticeDarkInverseOnSurface,
    outline = LatticeDarkOutline,
    outlineVariant = LatticeDarkOutlineVariant,
    scrim = LatticeDarkScrim,
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
    /** The floor every interactive element's touch area is held at, independent of how small it
     * is allowed to *look*. */
    val touchTarget: Dp = 48.dp,
    /** A text field at rest. */
    val field: Dp = 56.dp,
    /** A text field in the collapsed control region: shorter, but never below [touchTarget]. */
    val fieldCompact: Dp = 48.dp,
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
