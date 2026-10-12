// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pacien.tincapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val ContentWidth = 600.dp

/** Emphasized decelerate: the Material curve for things arriving on screen. */
internal val EnterEasing = CubicBezierEasing(.05f, .7f, .1f, 1f)

/** Container colour for list segments; sheets raise it so segments stay visible on the sheet. */
internal val LocalSegmentColor = compositionLocalOf<Color?> { null }

/** Segmented list item shape: large outer corners on the group, small corners between items. */
internal fun segmentShape(index: Int, count: Int): Shape {
  val top = if (index == 0) 20.dp else 4.dp
  val bottom = if (index == count - 1) 20.dp else 4.dp
  return RoundedCornerShape(top, top, bottom, bottom)
}

class GroupScope internal constructor() {
  internal val items = mutableListOf<@Composable () -> Unit>()
  fun item(content: @Composable () -> Unit) { items += content }
}

/** A segmented list: each item is its own surface, separated by a 2 dp gap. */
@Composable
internal fun Group(modifier: Modifier = Modifier, build: GroupScope.() -> Unit) {
  val items = GroupScope().apply(build).items
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    items.forEachIndexed { index, content -> Segment(index, items.size) { content() } }
  }
}

@Composable
internal fun Segment(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(modifier.fillMaxWidth(), shape = segmentShape(index, count),
    color = LocalSegmentColor.current ?: MaterialTheme.colorScheme.surfaceContainer, content = content)
}

/** Section header in the Material settings style. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
  Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    trailing()
  }
}

/** A list row. [supporting] goes under the headline; [value] sits on the trailing edge. */
@Composable
internal fun ListRow(
  title: String,
  modifier: Modifier = Modifier,
  supporting: String? = null,
  value: String? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val alpha = if (enabled) 1f else .38f
  Row(modifier.fillMaxWidth()
    .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
    .heightIn(min = if (supporting == null) 56.dp else 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically) {
    if (leading != null) {
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) { leading() }
      Spacer(Modifier.width(16.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium.merge(Figures),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (value != null) Text(value, Modifier.padding(start = 16.dp).widthIn(max = 200.dp), style = MaterialTheme.typography.bodyMedium.merge(Figures),
      color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (trailing != null) {
      Spacer(Modifier.width(12.dp))
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { trailing() }
    }
  }
}

internal enum class Signal { Online, Waiting, Idle, Down }

@Composable
internal fun Signal.color(): Color {
  val mesh = LocalMeshColors.current
  return when (this) {
    Signal.Online -> mesh.online; Signal.Waiting -> mesh.waiting
    Signal.Idle -> mesh.idle; Signal.Down -> MaterialTheme.colorScheme.error
  }
}

/** A status dot; [pulse] adds an expanding ring while a connection is being established. */
@Composable
internal fun StatusDot(signal: Signal, modifier: Modifier = Modifier, size: Dp = 8.dp, pulse: Boolean = false) {
  val color by animateColorAsState(signal.color(), Motion.effects(), label = "signal")
  val ring = if (pulse) rememberInfiniteTransition(label = "pulse")
    .animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing)), label = "ring").value else 0f
  Box(modifier.size(size).drawBehind {
    if (pulse) drawCircle(color.copy(alpha = .35f * (1 - ring)), radius = this.size.minDimension / 2 * (1 + ring * 1.6f))
  }.background(color, CircleShape))
}

/** A monogram avatar with a connection badge. */
@Composable
internal fun Avatar(name: String, signal: Signal?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
  Box(modifier.size(size)) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
      Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", color = MaterialTheme.colorScheme.onSecondaryContainer,
        style = if (size > 48.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
    }
    if (signal != null) {
      val badge by animateColorAsState(signal.color(), Motion.effects(), label = "badge")
      val ring = LocalSegmentColor.current ?: MaterialTheme.colorScheme.surfaceContainer
      Box(Modifier.align(Alignment.BottomEnd).size(size * .32f).background(ring, CircleShape).padding(2.dp).background(badge, CircleShape))
    }
  }
}

/** Corner radius that tightens while pressed, the Expressive button press response. */
@Composable
internal fun pressShape(interaction: MutableInteractionSource): Shape {
  val pressed by interaction.collectIsPressedAsState()
  val percent by animateIntAsState(if (pressed) 22 else 50, Motion.spatialFast(), label = "press")
  return RoundedCornerShape(percent)
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
  val interaction = remember { MutableInteractionSource() }
  Button(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled && !busy, shape = pressShape(interaction), interactionSource = interaction) {
    if (busy) Spinner() else Text(text, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val interaction = remember { MutableInteractionSource() }
  OutlinedButton(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled, shape = pressShape(interaction), interactionSource = interaction) {
    Text(text, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
internal fun Spinner(color: Color = LocalContentColor.current) =
  CircularProgressIndicator(Modifier.size(20.dp), color = color, strokeWidth = 2.5.dp)

/** Whether [Appear] animates; off when a screen is shown again after navigating back to it. */
internal val LocalAppear = compositionLocalOf { true }

/** Fades and rises into place once, [order] steps after the screen first appears. */
@Composable
internal fun Appear(order: Int, content: @Composable () -> Unit) {
  val animate = LocalAppear.current
  val state = remember { MutableTransitionState(!animate).apply { targetState = true } }
  AnimatedVisibility(state, enter = fadeIn(tween(300, delayMillis = order * 60)) +
    slideInVertically(tween(500, delayMillis = order * 60, easing = EnterEasing)) { it / 5 }) { content() }
}

/** The Tincer mark; with [animate], its nodes light up in turn like packets moving around the mesh. */
@Composable
internal fun MeshMark(modifier: Modifier, color: Color, node: Color = color, animate: Boolean = false) {
  val phase = if (animate) rememberInfiniteTransition(label = "mesh")
    .animateFloat(0f, 3f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "hop").value else -1f
  Canvas(modifier) {
    val radius = size.minDimension * .105f
    val points = listOf(Offset(size.width * .5f, size.height * .14f), Offset(size.width * .86f, size.height * .79f), Offset(size.width * .14f, size.height * .79f))
    for (i in points.indices) drawLine(color.copy(alpha = .45f), points[i], points[(i + 1) % 3], size.minDimension * .05f, StrokeCap.Round)
    points.forEachIndexed { i, point ->
      // Distance from this node to the travelling highlight, wrapping around the triangle.
      val distance = if (phase < 0) 1f else minOf(kotlin.math.abs(phase - i), 3 - kotlin.math.abs(phase - i)).coerceAtMost(1f)
      val glow = 1 - distance
      if (glow > 0) drawCircle(node.copy(alpha = .9f * glow), radius * 1.1f * glow, point)
      drawCircle(color, radius * 1.45f, point, style = Stroke(size.minDimension * .045f))
    }
    drawCircle(node, radius * .5f, Offset(size.width * .5f, size.height * .56f))
  }
}

/** Bottom sheet frame shared by every sheet: title, optional subtitle, scrollable body, trailing action. */
@Composable
internal fun MeshSheet(
  title: String,
  onDismiss: () -> Unit,
  subtitle: String? = null,
  dismissible: Boolean = true,
  headerAction: (@Composable () -> Unit)? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(onDismissRequest = { if (dismissible) onDismiss() }, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    sheetGesturesEnabled = dismissible) {
    CompositionLocalProvider(LocalSegmentColor provides MaterialTheme.colorScheme.surfaceContainerHigh) {
      SheetBody(title, subtitle, headerAction, footer, content)
    }
  }
}

@Composable
internal fun SheetBody(
  title: String,
  subtitle: String? = null,
  headerAction: (@Composable () -> Unit)? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().imePadding()) {
    Row(Modifier.padding(start = 24.dp, end = 12.dp, bottom = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
      headerAction?.invoke()
    }
    if (subtitle != null) Text(subtitle, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    if (footer != null) Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp), content = footer)
    else Spacer(Modifier.height(16.dp))
  }
}
