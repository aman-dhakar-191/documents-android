package io.github.amandhakar.documents.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colours the Material scheme has no slot for: a "good" green and the amber used for warnings. */
@Immutable
class DocsColors(val good: Color, val warnBackground: Color, val warnText: Color)

private val LightExtras = DocsColors(Color(0xFF1B7A4B), Color(0xFFFDEBD3), Color(0xFF8A4B00))
private val DarkExtras = DocsColors(Color(0xFF5FD39A), Color(0xFF3A2A10), Color(0xFFFFC77A))

val LocalDocsColors = staticCompositionLocalOf { LightExtras }

/** The extra colours for the current theme: `docsColors.good`. */
val docsColors: DocsColors
    @Composable get() = LocalDocsColors.current

// A fixed scheme, so the app looks the same on every phone whatever the wallpaper is. Cards are the
// surface colour (white in light mode) on a warm off-white background.
internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1D5FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECFE),
    onPrimaryContainer = Color(0xFF0E3A8F),
    secondaryContainer = Color(0xFFE3ECFE),
    onSecondaryContainer = Color(0xFF0E3A8F),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFECEAE3),
    onSurfaceVariant = Color(0xFF5A606C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF7C828E),
    outlineVariant = Color(0xFFE6E4DD),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8EB6FF),
    onPrimary = Color(0xFF0A2A66),
    primaryContainer = Color(0xFF1D2F57),
    onPrimaryContainer = Color(0xFFC9DBFF),
    secondaryContainer = Color(0xFF1D2F57),
    onSecondaryContainer = Color(0xFFC9DBFF),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF20242C),
    onSurfaceVariant = Color(0xFFA6ADBA),
    surfaceContainerLowest = Color(0xFF171A20),
    surfaceContainerLow = Color(0xFF171A20),
    surfaceContainer = Color(0xFF171A20),
    surfaceContainerHigh = Color(0xFF171A20),
    surfaceContainerHighest = Color(0xFF171A20),
    outline = Color(0xFF8A919E),
    outlineVariant = Color(0xFF2B303A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val DocsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The app's look: the fixed colours (light or dark with the phone's setting) and rounder corners. */
@Composable
fun DocsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalDocsColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = DocsShapes, content = content)
    }
}
