// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Connection-state colours, which Material 3 has no roles for. */
@Immutable
data class MeshColors(val online: Color, val waiting: Color, val idle: Color)

private val LightMesh = MeshColors(online = Color(0xFF1E8E3E), waiting = Color(0xFFB06000), idle = Color(0xFF8F8F99))
private val DarkMesh = MeshColors(online = Color(0xFF6DD58C), waiting = Color(0xFFFFB86C), idle = Color(0xFF6E6E78))

internal val LocalMeshColors = staticCompositionLocalOf { LightMesh }

// Tonal schemes generated from Tincer's indigo, used where wallpaper colours are unavailable (before Android 12).
internal val BrandLight = lightColorScheme(
  primary = Color(0xFF4355B9), onPrimary = Color.White, primaryContainer = Color(0xFFDEE0FF), onPrimaryContainer = Color(0xFF00105C),
  secondary = Color(0xFF5B5D72), onSecondary = Color.White, secondaryContainer = Color(0xFFE0E1F9), onSecondaryContainer = Color(0xFF181A2C),
  tertiary = Color(0xFF77536D), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFD7F1), onTertiaryContainer = Color(0xFF2D1228),
  error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
  background = Color(0xFFFBF8FF), onBackground = Color(0xFF1B1B21), surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1B1B21),
  surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF46464F), outline = Color(0xFF777680), outlineVariant = Color(0xFFC7C5D0),
  inverseSurface = Color(0xFF303036), inverseOnSurface = Color(0xFFF2EFF7), inversePrimary = Color(0xFFBAC3FF), surfaceTint = Color(0xFF4355B9),
  surfaceDim = Color(0xFFDBD9E0), surfaceBright = Color(0xFFFBF8FF), surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF5F2FA), surfaceContainer = Color(0xFFEFEDF4), surfaceContainerHigh = Color(0xFFEAE7EF),
  surfaceContainerHighest = Color(0xFFE4E1E9),
)

internal val BrandDark = darkColorScheme(
  primary = Color(0xFFBAC3FF), onPrimary = Color(0xFF08218A), primaryContainer = Color(0xFF2A3CA0), onPrimaryContainer = Color(0xFFDEE0FF),
  secondary = Color(0xFFC4C5DD), onSecondary = Color(0xFF2D2F42), secondaryContainer = Color(0xFF434659), onSecondaryContainer = Color(0xFFE0E1F9),
  tertiary = Color(0xFFE6BAD7), onTertiary = Color(0xFF44263D), tertiaryContainer = Color(0xFF5D3C55), onTertiaryContainer = Color(0xFFFFD7F1),
  error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
  background = Color(0xFF121318), onBackground = Color(0xFFE4E1E9), surface = Color(0xFF121318), onSurface = Color(0xFFE4E1E9),
  surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0), outline = Color(0xFF91909A), outlineVariant = Color(0xFF46464F),
  inverseSurface = Color(0xFFE4E1E9), inverseOnSurface = Color(0xFF303036), inversePrimary = Color(0xFF4355B9), surfaceTint = Color(0xFFBAC3FF),
  surfaceDim = Color(0xFF121318), surfaceBright = Color(0xFF39393F), surfaceContainerLowest = Color(0xFF0D0E13),
  surfaceContainerLow = Color(0xFF1B1B21), surfaceContainer = Color(0xFF1F1F25), surfaceContainerHigh = Color(0xFF29292F),
  surfaceContainerHighest = Color(0xFF34343A),
)

internal val MeshTypography = Typography().run {
  copy(
    displaySmall = displaySmall.copy(letterSpacing = (-0.5).sp),
    headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium),
  )
}

/** Tabular figures keep addresses and latencies aligned without a monospace font. */
internal val Figures = TextStyle(fontFeatureSettings = "tnum")
internal val Code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)

/**
 * Spring tokens from the Material 3 Expressive motion scheme. Spatial springs move and resize things and may
 * overshoot; effects springs change colour and opacity and never do.
 */
internal object Motion {
  fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = .8f, stiffness = 380f)
  fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = .6f, stiffness = 800f)
  fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = .8f, stiffness = 200f)
  fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
  fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

@Composable
fun MeshTheme(appearance: Appearance, content: @Composable () -> Unit) {
  val dark = when (appearance) { Appearance.System -> isSystemInDarkTheme(); Appearance.Light -> false; Appearance.Dark -> true }
  CompositionLocalProvider(LocalMeshColors provides if (dark) DarkMesh else LightMesh) {
    PlatformTheme(dark, if (dark) BrandDark else BrandLight, MeshTypography, content)
  }
}
