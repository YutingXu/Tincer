// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pacien.tincapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ConnectionPhase.transitioning get() = this == ConnectionPhase.Starting || this == ConnectionPhase.Stopping

internal fun MeshState.signal() = when (phase) {
  ConnectionPhase.Active -> if (onlinePeers > 0) Signal.Online else Signal.Waiting
  ConnectionPhase.Starting, ConnectionPhase.Stopping -> Signal.Waiting
  ConnectionPhase.Offline -> Signal.Idle
}

internal fun MeshState.statusLabel() = when (phase) {
  ConnectionPhase.Active -> if (onlinePeers > 0) "Connected" else "Waiting for peers"
  ConnectionPhase.Starting -> "Connecting"
  ConnectionPhase.Stopping -> "Disconnecting"
  ConnectionPhase.Offline -> "Disconnected"
}

private fun MeshPeer.signal(phase: ConnectionPhase) = when {
  reachable -> Signal.Online
  phase == ConnectionPhase.Active && transport == "Offline" -> Signal.Down
  phase == ConnectionPhase.Active -> Signal.Waiting
  else -> Signal.Idle
}

private fun peerKey(name: String) = "peer-$name"

@Composable
private fun TopBar(content: @Composable RowScope.() -> Unit) {
  Row(Modifier.widthIn(max = ContentWidth).fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
internal fun HomeScreen(
  state: MeshState,
  busy: Boolean,
  onConnect: () -> Unit,
  onNetworks: () -> Unit,
  onSettings: () -> Unit,
  onJoin: () -> Unit,
  onCreate: () -> Unit,
  onAddPeer: () -> Unit,
  onPeer: (MeshPeer) -> Unit,
  onCopy: (String) -> Unit,
  listState: LazyListState = rememberLazyListState(),
) {
  val profile = state.profile
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    TopBar {
      if (profile == null) {
        Text("Tincer", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
      } else {
        Row(Modifier.clip(CircleShape).clickable(onClick = onNetworks).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Text(profile.name, Modifier.widthIn(max = 220.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Icon(Icons.Outlined.ArrowDropDown, "Switch network", Modifier.padding(start = 2.dp))
        }
      }
      Spacer(Modifier.weight(1f))
      IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
    }
    Box(Modifier.height(4.dp).fillMaxWidth()) { if (busy) LinearProgressIndicator(Modifier.fillMaxSize()) }
    if (profile == null) Welcome(onJoin, onCreate) else Dashboard(state, profile, listState, onConnect, onAddPeer, onPeer, onCopy)
  }
}

@Composable
private fun Welcome(onJoin: () -> Unit, onCreate: () -> Unit) {
  val spin by rememberInfiniteTransition(label = "welcome").animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "spin")
  BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).heightIn(min = maxHeight).navigationBarsPadding().padding(24.dp),
      verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
      Appear(0) {
        Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
          Box(Modifier.matchParentSize().graphicsLayer { rotationZ = spin }.clip(rememberPowerShape(1f))
            .background(MaterialTheme.colorScheme.primaryContainer))
          MeshMark(Modifier.size(64.dp), MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary, animate = true)
        }
      }
      Appear(1) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("No networks yet", Modifier.padding(top = 32.dp), style = MaterialTheme.typography.headlineMedium)
          Text("Join an existing tinc network with an invitation, or create a new one on this device.", Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
      }
      Appear(2) {
        Column {
          PrimaryButton("Join network", onJoin, Modifier.padding(top = 40.dp))
          SecondaryButton("Create network", onCreate, Modifier.padding(top = 12.dp))
        }
      }
    }
  }
}

@Composable
private fun Dashboard(state: MeshState, profile: MeshProfile, listState: LazyListState, onConnect: () -> Unit, onAddPeer: () -> Unit, onPeer: (MeshPeer) -> Unit, onCopy: (String) -> Unit) {
  var query by rememberSaveable(profile.name) { mutableStateOf("") }
  val peers = state.peers.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.address.contains(query.trim(), true) }
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  LazyColumn(Modifier.widthIn(max = ContentWidth).fillMaxSize(), state = listState,
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom)) {
    item(key = "status") { Appear(0) { StatusHero(state, profile, onConnect, onCopy) } }
    profile.problem?.let { problem -> item(key = "problem") {
      Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
        Text(problem, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
      }
    } }
    item(key = "peers-label") {
      Appear(1) {
        SectionLabel("Peers", Modifier.padding(top = 16.dp, bottom = 4.dp)) {
          FilledTonalIconButton(onClick = onAddPeer) { Icon(Icons.Outlined.Add, "Add peer") }
        }
      }
    }
    if (state.peers.size > 6 || query.isNotEmpty()) item(key = "search") {
      OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 12.dp), placeholder = { Text("Search peers") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = CircleShape)
    }
    when {
      state.peers.isEmpty() -> item(key = "empty") {
        Appear(2) {
          Group {
            item {
              Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Devices, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No peers", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                Text("Exchange public keys with another tinc node to connect to it.", Modifier.padding(top = 4.dp),
                  style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                FilledTonalButton(onAddPeer, Modifier.padding(top = 20.dp)) {
                  Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add peer")
                }
              }
            }
          }
        }
      }
      peers.isEmpty() -> item(key = "no-match") {
        Text("No peers match “${query.trim()}”.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      else -> itemsIndexed(peers, key = { _, peer -> peer.name }) { index, peer ->
        Segment(index, peers.size, Modifier.animateItem().padding(bottom = 2.dp).sharedContainer(peerKey(peer.name), segmentShape(index, peers.size))) {
          PeerRow(peer, state.phase) { onPeer(peer) }
        }
      }
    }
  }
}

@Composable
private fun StatusHero(state: MeshState, profile: MeshProfile, onConnect: () -> Unit, onCopy: (String) -> Unit) {
  val phase = state.phase
  val scheme = MaterialTheme.colorScheme
  val (containerTarget, contentTarget) = when {
    phase == ConnectionPhase.Offline -> scheme.surfaceContainer to scheme.onSurface
    phase.transitioning -> scheme.secondaryContainer to scheme.onSecondaryContainer
    state.onlinePeers == 0 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    else -> scheme.primaryContainer to scheme.onPrimaryContainer
  }
  val container by animateColorAsState(containerTarget, Motion.effectsSlow(), label = "hero")
  val content by animateColorAsState(contentTarget, Motion.effectsSlow(), label = "hero-content")
  Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = container, contentColor = content) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      PowerButton(state, onConnect)
      AnimatedContent(state.statusLabel(), Modifier.padding(top = 24.dp), label = "status", transitionSpec = {
        (slideInVertically(Motion.spatial()) { it } + fadeIn(Motion.effects())) togetherWith
          (slideOutVertically(Motion.spatial()) { -it } + fadeOut(Motion.effects())) using SizeTransform(clip = false)
      }) { label -> Text(label, style = MaterialTheme.typography.headlineSmall) }
      Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(profile.address ?: "No address", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge.merge(Figures),
          maxLines = 1, overflow = TextOverflow.Ellipsis)
        profile.address?.let { address ->
          IconButton(onClick = { onCopy(address) }, Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy address", Modifier.size(18.dp)) }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        val style = MaterialTheme.typography.bodyMedium.merge(Figures)
        val muted = LocalContentColor.current.copy(alpha = .72f)
        Text("${profile.nodeName.ifEmpty { "This device" }} · ", style = style, color = muted)
        when {
          state.peers.isEmpty() -> Text("no peers", style = style, color = muted)
          phase == ConnectionPhase.Active -> {
            RollingNumber(state.onlinePeers, style, muted)
            Text(" of ${state.peers.size} online", style = style, color = muted)
          }
          else -> Text("${state.peers.size} peer${if (state.peers.size == 1) "" else "s"}", style = style, color = muted)
        }
      }
    }
  }
}

/** A number that rolls up or down when it changes. */
@Composable
private fun RollingNumber(value: Int, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
  AnimatedContent(value, label = "count", transitionSpec = {
    val up = targetState > initialState
    (slideInVertically(Motion.spatial()) { if (up) it else -it } + fadeIn(Motion.effects())) togetherWith
      (slideOutVertically(Motion.spatial()) { if (up) -it else it } + fadeOut(Motion.effects())) using SizeTransform(clip = false)
  }) { Text("$it", style = style, color = color) }
}

/**
 * The connect toggle. Off it is a quiet circle; on it morphs into a primary-coloured cookie and settles with a small
 * turn. While connecting or disconnecting it spins, standing in for a loading indicator.
 */
@Composable
private fun PowerButton(state: MeshState, onClick: () -> Unit) {
  val phase = state.phase
  val scheme = MaterialTheme.colorScheme
  val (containerTarget, iconTarget) = when {
    phase == ConnectionPhase.Offline -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    phase.transitioning -> scheme.secondary to scheme.onSecondary
    state.onlinePeers == 0 -> scheme.tertiary to scheme.onTertiary
    else -> scheme.primary to scheme.onPrimary
  }
  val container by animateColorAsState(containerTarget, Motion.effectsSlow(), label = "power")
  val icon by animateColorAsState(iconTarget, Motion.effectsSlow(), label = "power-icon")
  val morph by animateFloatAsState(if (phase == ConnectionPhase.Active || phase == ConnectionPhase.Starting) 1f else 0f, Motion.spatialSlow(), label = "morph")
  val settle by animateFloatAsState(if (phase == ConnectionPhase.Active) 40f else 0f, Motion.spatialSlow(), label = "settle")
  val spin = if (phase.transitioning) rememberInfiniteTransition(label = "spin")
    .animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin").value else 0f
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) .9f else 1f, Motion.spatialFast(), label = "press")
  val action = if (phase == ConnectionPhase.Offline) "Connect" else "Disconnect"
  Box(Modifier.size(128.dp).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
    Box(Modifier.matchParentSize().graphicsLayer { rotationZ = settle + spin }.clip(rememberPowerShape(morph)).background(container)
      .clickable(interaction, LocalIndication.current, enabled = !phase.transitioning, role = Role.Button, onClick = onClick)
      .semantics { contentDescription = action; stateDescription = state.statusLabel() })
    Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(44.dp), tint = icon)
  }
}

@Composable
private fun PeerRow(peer: MeshPeer, phase: ConnectionPhase, onClick: () -> Unit) {
  val live = phase == ConnectionPhase.Active
  ListRow(peer.name, onClick = onClick,
    supporting = if (live) listOf(peer.transport, peer.address).filter { it.isNotEmpty() }.joinToString(" · ") else "Saved",
    leading = { Avatar(peer.name, peer.signal(phase)) },
    trailing = if (peer.latency.isNotEmpty()) ({ Text(peer.latency, style = MaterialTheme.typography.labelLarge.merge(Figures)) }) else null)
}

@Composable
internal fun PeerScreen(peer: MeshPeer, phase: ConnectionPhase, onBack: () -> Unit, onCopy: (String) -> Unit) {
  val signal = peer.signal(phase)
  Surface(Modifier.fillMaxSize().sharedContainer(peerKey(peer.name), RoundedCornerShape(20.dp)), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      TopBar {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Spacer(Modifier.weight(1f))
        if (peer.details.isNotEmpty()) IconButton(onClick = { onCopy(peer.details) }) { Icon(Icons.Outlined.ContentCopy, "Copy details") }
      }
      Column(Modifier.widthIn(max = ContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Avatar(peer.name, signal, size = 88.dp)
          Text(peer.name, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium)
          Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(signal)
            Text(if (phase == ConnectionPhase.Active) peer.transport else "Not connected", Modifier.padding(start = 8.dp),
              style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (peer.address.isNotEmpty() || peer.latency.isNotEmpty()) Group {
          if (peer.address.isNotEmpty()) item {
            ListRow("Address", value = peer.address, onClick = { onCopy(peer.address.substringBefore('/')) },
              trailing = { Icon(Icons.Outlined.ContentCopy, "Copy address", Modifier.size(18.dp)) })
          }
          if (peer.latency.isNotEmpty()) item { ListRow("Round trip", value = peer.latency) }
        }
        val details = MeshPeer.details(peer.details)
        if (details.isNotEmpty()) {
          SectionLabel("Details", Modifier.padding(top = 12.dp))
          Group { details.forEach { (label, value) -> item { DetailRow(label, value) } } }
        } else Text("Connect to see how this peer is reached.", Modifier.fillMaxWidth().padding(top = 8.dp),
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
    Text(label, Modifier.padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
    SelectionContainer(Modifier.weight(1f)) {
      Text(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge.merge(Figures),
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
  }
}

@Composable
internal fun SettingsScreen(
  state: MeshState,
  version: String,
  onBack: () -> Unit,
  onNetworkSettings: () -> Unit,
  onShareKey: () -> Unit,
  onAppearance: (Appearance) -> Unit,
  onNotifications: () -> Unit,
  onLog: () -> Unit,
) {
  val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
    LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scroll,
      navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer))
    Column(Modifier.widthIn(max = ContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
      .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
      state.profile?.let { profile ->
        val configuration = profile.configuration
        SectionLabel("Network")
        Group {
          item { ListRow("Name", supporting = profile.name, leading = { Icon(Icons.Outlined.Hub, null) }) }
          item { ListRow("Device", supporting = profile.nodeName.ifEmpty { "—" }, leading = { Icon(Icons.Outlined.PhoneAndroid, null) }) }
          item { ListRow("Addresses", supporting = configuration.addresses.joinToString().ifEmpty { "None" }, onClick = onNetworkSettings,
            leading = { Icon(Icons.Outlined.Lan, null) }) }
          item { ListRow("Routes", supporting = configuration.routes.joinToString().ifEmpty { "None" }, onClick = onNetworkSettings,
            leading = { Icon(Icons.Outlined.Route, null) }) }
          item { ListRow("DNS", supporting = configuration.dnsServers.joinToString().ifEmpty { "Default" }, onClick = onNetworkSettings,
            leading = { Icon(Icons.Outlined.Dns, null) }) }
          item { ListRow("Excluded apps", onClick = onNetworkSettings, leading = { Icon(Icons.Outlined.Apps, null) },
            supporting = configuration.disallowedApplications.size.let { if (it == 0) "None" else "$it app${if (it == 1) "" else "s"}" }) }
          item { ListRow("Share public key", onClick = onShareKey, enabled = !state.busy, leading = { Icon(Icons.Outlined.Share, null) }) }
        }
        if (state.phase != ConnectionPhase.Offline) Text("Changes apply the next time you connect.", Modifier.padding(start = 16.dp, top = 8.dp),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
      }
      SectionLabel("App")
      Group {
        item {
          Column {
            ListRow("Appearance", leading = { Icon(Icons.Outlined.Palette, null) })
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
              Appearance.entries.forEachIndexed { index, appearance ->
                SegmentedButton(selected = state.appearance == appearance, onClick = { onAppearance(appearance) },
                  shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size)) { Text(appearance.name) }
              }
            }
          }
        }
        item { ListRow("Notifications", onClick = onNotifications, leading = { Icon(Icons.Outlined.Notifications, null) },
          trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(20.dp)) }) }
      }
      Spacer(Modifier.height(16.dp))
      SectionLabel("Diagnostics")
      Group { item { ListRow("Connection log", onClick = onLog, leading = { Icon(Icons.Outlined.Terminal, null) }) } }
      Text("Tincer $version\ntinc 1.1 (211e3df) · GPL-3.0-or-later", Modifier.fillMaxWidth().padding(top = 32.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
  }
}

@Composable
internal fun NetworksSheet(state: MeshState, onSelect: (String) -> Unit, onJoin: () -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit) {
  val locked = state.phase != ConnectionPhase.Offline
  MeshSheet("Networks", onDismiss, subtitle = if (locked) "Disconnect to switch networks." else null) {
    Group {
      state.profiles.forEach { profile ->
        val selected = profile.name == state.selectedName
        item {
          ListRow(profile.name, supporting = profile.address ?: "No address", enabled = selected || !locked, onClick = { onSelect(profile.name) },
            leading = { Icon(Icons.Outlined.Hub, null) },
            trailing = { if (selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary) })
        }
      }
    }
    Group {
      item { ListRow("Join network", onClick = onJoin, leading = { Icon(Icons.Outlined.QrCodeScanner, null) }) }
      item { ListRow("Create network", onClick = onCreate, leading = { Icon(Icons.Outlined.Add, null) }) }
    }
  }
}

@Composable
internal fun LogSheet(log: String?, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
  MeshSheet("Connection log", onDismiss, headerAction = {
    IconButton(onClick = { log?.let(onCopy) }, enabled = log != null) { Icon(Icons.Outlined.ContentCopy, "Copy log") }
  }) {
    if (log == null) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else CodeBlock(log, Modifier.heightIn(max = 520.dp), scrollToEnd = true)
  }
}

@Composable
internal fun CodeBlock(text: String, modifier: Modifier = Modifier, scrollToEnd: Boolean = false) {
  val scroll = rememberScrollState()
  if (scrollToEnd) LaunchedEffect(text, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
  Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    SelectionContainer(modifier.verticalScroll(scroll)) {
      Text(text, Modifier.padding(16.dp), style = Code)
    }
  }
}
