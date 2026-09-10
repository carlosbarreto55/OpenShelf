package com.example.paperReader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette sampled from `docs/ui/lattice-search-reference.png`.
 *
 * Role mapping used by the reference screen:
 * - [LatticeDarkBackground] page canvas, [LatticeDarkSurfaceContainer] cards / search field / nav bar
 * - [LatticeDarkPrimary] filled accent actions ("Search", "Download PDF") and access affordances
 * - [LatticeDarkPrimaryContainer] the active filter chip
 * - [LatticeDarkSecondaryContainer] the selected navigation indicator (Material 3 draws it from
 *   this role), matching the active chip's green pill
 * - [LatticeDarkTertiaryContainer] the amber "Read online" action
 * - [LatticeDarkSurfaceVariant] neutral metadata pills (subject tags)
 */

// Dark (the reference look).
val LatticeDarkPrimary = Color(0xFFB9EBCE)
val LatticeDarkOnPrimary = Color(0xFF042E18)
val LatticeDarkPrimaryContainer = Color(0xFF183426)
val LatticeDarkOnPrimaryContainer = Color(0xFFCBEDDC)
val LatticeDarkSecondary = Color(0xFFA9D8BE)
val LatticeDarkOnSecondary = Color(0xFF12301F)
val LatticeDarkSecondaryContainer = Color(0xFF183426)
val LatticeDarkOnSecondaryContainer = Color(0xFFCBEDDC)
val LatticeDarkTertiary = Color(0xFFE9C98A)
val LatticeDarkOnTertiary = Color(0xFF3A2A05)
val LatticeDarkTertiaryContainer = Color(0xFF392D17)
val LatticeDarkOnTertiaryContainer = Color(0xFFF5DCA8)
val LatticeDarkError = Color(0xFFFFB4AB)
val LatticeDarkOnError = Color(0xFF690005)
val LatticeDarkErrorContainer = Color(0xFF93000A)
val LatticeDarkOnErrorContainer = Color(0xFFFFDAD6)
val LatticeDarkBackground = Color(0xFF121411)
val LatticeDarkOnBackground = Color(0xFFF2F5F0)
val LatticeDarkSurface = Color(0xFF121411)
val LatticeDarkOnSurface = Color(0xFFF2F5F0)
val LatticeDarkSurfaceContainerLowest = Color(0xFF0D0F0C)
val LatticeDarkSurfaceContainerLow = Color(0xFF161A15)
val LatticeDarkSurfaceContainer = Color(0xFF191E18)
val LatticeDarkSurfaceContainerHigh = Color(0xFF1C211B)
val LatticeDarkSurfaceContainerHighest = Color(0xFF1F241E)
val LatticeDarkSurfaceVariant = Color(0xFF232821)
val LatticeDarkOnSurfaceVariant = Color(0xFFB2B9B1)
// Lightened from the sampled #3C423B: that value only reaches ~1.8:1 against the dark background,
// short of the WCAG 1.4.11 3:1 floor for a UI-component boundary (e.g. OutlinedTextField's border).
val LatticeDarkOutline = Color(0xFF667064)
val LatticeDarkOutlineVariant = Color(0xFF333832)
val LatticeDarkInverseSurface = Color(0xFFE2E5DF)
val LatticeDarkInverseOnSurface = Color(0xFF191E18)
val LatticeDarkInversePrimary = Color(0xFF116B46)
val LatticeDarkScrim = Color(0xFF000000)

// Light counterpart: same mint/amber identity on a paper-white canvas.
val LatticeLightPrimary = Color(0xFF116B46)
val LatticeLightOnPrimary = Color(0xFFFFFFFF)
val LatticeLightPrimaryContainer = Color(0xFFB9EBCE)
val LatticeLightOnPrimaryContainer = Color(0xFF00210F)
val LatticeLightSecondary = Color(0xFF386552)
val LatticeLightOnSecondary = Color(0xFFFFFFFF)
val LatticeLightSecondaryContainer = Color(0xFFC6E7D4)
val LatticeLightOnSecondaryContainer = Color(0xFF032116)
val LatticeLightTertiary = Color(0xFF7A5A15)
val LatticeLightOnTertiary = Color(0xFFFFFFFF)
val LatticeLightTertiaryContainer = Color(0xFFF7E0B0)
val LatticeLightOnTertiaryContainer = Color(0xFF281900)
val LatticeLightError = Color(0xFFBA1A1A)
val LatticeLightOnError = Color(0xFFFFFFFF)
val LatticeLightErrorContainer = Color(0xFFFFDAD6)
val LatticeLightOnErrorContainer = Color(0xFF410002)
val LatticeLightBackground = Color(0xFFF7F9F5)
val LatticeLightOnBackground = Color(0xFF171D16)
val LatticeLightSurface = Color(0xFFF7F9F5)
val LatticeLightOnSurface = Color(0xFF171D16)
val LatticeLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LatticeLightSurfaceContainerLow = Color(0xFFF2F5F0)
val LatticeLightSurfaceContainer = Color(0xFFECEFEA)
val LatticeLightSurfaceContainerHigh = Color(0xFFE6EAE4)
val LatticeLightSurfaceContainerHighest = Color(0xFFE0E5DE)
val LatticeLightSurfaceVariant = Color(0xFFDDE5DC)
val LatticeLightOnSurfaceVariant = Color(0xFF414942)
val LatticeLightOutline = Color(0xFF71796F)
val LatticeLightOutlineVariant = Color(0xFFC1C9BE)
val LatticeLightInverseSurface = Color(0xFF2C322B)
val LatticeLightInverseOnSurface = Color(0xFFEDF1EA)
val LatticeLightInversePrimary = Color(0xFFB9EBCE)
val LatticeLightScrim = Color(0xFF000000)
