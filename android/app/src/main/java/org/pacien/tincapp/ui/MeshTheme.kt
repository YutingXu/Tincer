// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

private val Light = lightColorScheme(
  primary = Color(0xFF415CE3), onPrimary = Color.White,
  primaryContainer = Color(0xFFE9EDFF), onPrimaryContainer = Color(0xFF213486),
  background = Color(0xFFF6F7FA), onBackground = Color(0xFF171C28),
  surface = Color.White, onSurface = Color(0xFF171C28),
  surfaceVariant = Color(0xFFEEF0F5), onSurfaceVariant = Color(0xFF626B7D),
  outline = Color(0xFF8B93A3), outlineVariant = Color(0xFFE4E7EE),
  error = Color(0xFFB63442), errorContainer = Color(0xFFFFE9EB),
)
private val Dark = darkColorScheme(
  primary = Color(0xFFAAB9FF), onPrimary = Color(0xFF18265C),
  primaryContainer = Color(0xFF273555), onPrimaryContainer = Color(0xFFDCE3FF),
  background = Color(0xFF10131B), onBackground = Color(0xFFEDF0F8),
  surface = Color(0xFF191E29), onSurface = Color(0xFFEDF0F8),
  surfaceVariant = Color(0xFF222936), onSurfaceVariant = Color(0xFFA5AFC2),
  outline = Color(0xFF7C879B), outlineVariant = Color(0xFF2D3544),
)

@Composable
fun MeshTheme(appearance: Appearance, content: @Composable () -> Unit) {
  val dark = when (appearance) { Appearance.System -> isSystemInDarkTheme(); Appearance.Light -> false; Appearance.Dark -> true }
  val view = LocalView.current
  SideEffect {
    (view.context as? Activity)?.window?.let { window ->
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
      }
    }
  }
  MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
