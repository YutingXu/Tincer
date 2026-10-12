// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
package org.pacien.tincapp.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star

// Android-only pieces of the interface: wallpaper colours, the Expressive theme, shape morphing and shared
// element transitions. Everything else in the ui package is plain Compose.

@Composable
internal fun PlatformTheme(dark: Boolean, fallback: ColorScheme, typography: Typography, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else fallback
  val view = LocalView.current
  SideEffect {
    (view.context as? Activity)?.window?.let { window ->
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
      }
    }
  }
  MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = typography, content = content)
}

// A circle that morphs into a nine-lobed "cookie", the shape Material 3 Expressive uses for active toggles.
private val PowerMorph = Morph(
  RoundedPolygon.circle(numVertices = 9).normalized(),
  RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = .82f, rounding = CornerRounding(.5f), innerRounding = CornerRounding(.5f)).normalized(),
)

private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val cubics = morph.asCubics(progress)
    val path = Path()
    path.moveTo(cubics.first().anchor0X * size.width, cubics.first().anchor0Y * size.height)
    cubics.forEach {
      path.cubicTo(it.control0X * size.width, it.control0Y * size.height, it.control1X * size.width, it.control1Y * size.height,
        it.anchor1X * size.width, it.anchor1Y * size.height)
    }
    path.close()
    return Outline.Generic(path)
  }
}

/** The connect button's outline: 0 is a circle, 1 is a cookie. Values past either end come from spring overshoot. */
@Composable
internal fun rememberPowerShape(progress: Float): Shape = remember(progress) { MorphShape(PowerMorph, progress.coerceIn(0f, 1f)) }

private val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
private val LocalPageScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Hosts pages whose elements can transform into each other, such as a peer row into the peer page. */
@Composable
internal fun SharedPages(content: @Composable () -> Unit) {
  SharedTransitionLayout { CompositionLocalProvider(LocalSharedScope provides this) { content() } }
}

/** Marks [content] as one page of [SharedPages]; call it inside the page's AnimatedContent lambda. */
@Composable
internal fun AnimatedVisibilityScope.SharedPage(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalPageScope provides this) { content() }
}

/** Container transform: elements with the same [key] on two pages morph between each other's bounds. */
@Composable
internal fun Modifier.sharedContainer(key: String, shape: Shape): Modifier {
  val shared = LocalSharedScope.current ?: return this
  val page = LocalPageScope.current ?: return this
  return with(shared) {
    this@sharedContainer.sharedBounds(rememberSharedContentState(key), page,
      boundsTransform = { _, _ -> Motion.spatial() },
      resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
      clipInOverlayDuringTransition = OverlayClip(shape))
  }
}
