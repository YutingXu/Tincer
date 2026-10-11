// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pacien.tincapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pacien.tincapp.BuildConfig
import org.pacien.tincapp.utils.TincKeyring

internal enum class Page(val title: String, val icon: ImageVector) {
  Home("Home", Icons.Outlined.Home), Devices("Devices", Icons.Outlined.Devices),
  Networks("Networks", Icons.Outlined.Hub), Settings("Settings", Icons.Outlined.Settings)
}
internal enum class Sheet { None, AddNetwork, Join, Create, Peer, NetworkSettings, Appearance, Logs, Unlock }
private val SignalGreen = Color(0xFF54D7AA)

@Composable
fun MeshApp(model: MeshViewModel, onConnect: (String?) -> Unit, onShare: (String) -> Unit, onScan: () -> Unit, onNotifications: () -> Unit) {
  val state by model.state.collectAsStateWithLifecycle()
  var page by model.page
  var sheet by model.sheet
  var selectedPeer by remember { mutableStateOf<MeshPeer?>(null) }
  val snackbar = remember { SnackbarHostState() }
  val context = LocalContext.current
  LifecycleStartEffect(Unit) { model.observe(true); onStopOrDispose { model.observe(false) } }
  LaunchedEffect(state.notice) {
    state.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() }
  }
  val connect = {
    if (state.phase == ConnectionPhase.Active) model.disconnect()
    else if (state.profile != null && TincKeyring.needsPassphrase(state.profile!!.name)) sheet = Sheet.Unlock
    else onConnect(null)
  }
  val copy: (String) -> Unit = { text ->
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
      .setPrimaryClip(ClipData.newPlainText("VPN address", text))
  }

  MeshTheme(state.appearance) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MeshMark(Modifier.size(29.dp), MaterialTheme.colorScheme.primary)
            Text("tinc", Modifier.padding(start = 10.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.weight(1f))
            if (state.phase == ConnectionPhase.Active) {
              Box(Modifier.size(7.dp).background(Color(0xFF269977), CircleShape))
              Text("VPN active", Modifier.padding(start = 7.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
      },
      snackbarHost = { SnackbarHost(snackbar) },
      bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
          Page.entries.forEach { item -> NavigationBarItem(selected = page == item, onClick = { page = item },
            icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(23.dp)) },
            label = { Text(item.title, fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
        }
      }) { padding ->
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        when (page) {
          Page.Home -> HomeScreen(state, connect, { page = Page.Networks }, { page = Page.Devices },
            { sheet = Sheet.AddNetwork }, { sheet = Sheet.Peer }, { selectedPeer = it }, copy)
          Page.Devices -> DevicesScreen(state, { sheet = Sheet.Peer }, { selectedPeer = it })
          Page.Networks -> NetworksScreen(state, { model.select(it); page = Page.Home }, { sheet = Sheet.AddNetwork },
            { sheet = Sheet.NetworkSettings }, { model.exportPublic(onShare) })
          Page.Settings -> SettingsScreen(state, { sheet = it }, onNotifications)
        }
      }
    }

    when (sheet) {
      Sheet.None -> Unit
      Sheet.AddNetwork -> ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
          Text("Add a network", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
          Text("Bring your devices together.", Modifier.padding(top = 6.dp, bottom = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
          ActionRow(Icons.Outlined.QrCodeScanner, "Join with an invitation", "Scan a code or paste an invitation", { sheet = Sheet.Join })
          HorizontalDivider(Modifier.padding(vertical = 6.dp))
          ActionRow(Icons.Outlined.AddCircleOutline, "Create a network", "Start a mesh and add your peers", { sheet = Sheet.Create })
        }
      }
      Sheet.Create -> CreateNetworkDialog(state.busy, { sheet = Sheet.None }) { name, node, address, routes ->
        model.create(name, node, address, routes) { sheet = Sheet.None; page = Page.Home }
      }
      Sheet.Join -> JoinNetworkDialog(state.busy, state.scannedInvitation, { model.scanned(null) }, { sheet = Sheet.None }, onScan) { name, invitation ->
        model.join(name, invitation) { sheet = Sheet.None; page = Page.Home }
      }
      Sheet.Peer -> AddPeerDialog(state.busy, { sheet = Sheet.None }) { public, endpoint ->
        model.addPeer(public, endpoint) { sheet = Sheet.None }
      }
      Sheet.NetworkSettings -> state.profile?.let { profile ->
        NetworkSettingsDialog(profile, state.busy, { sheet = Sheet.None }) { address, routes, dns, apps ->
          model.saveSettings(address, routes, dns, apps) { sheet = Sheet.None }
        }
      }
      Sheet.Appearance -> AlertDialog(onDismissRequest = { sheet = Sheet.None }, title = { Text("Appearance") },
        text = { Column { Appearance.entries.forEach { appearance ->
          Row(Modifier.fillMaxWidth().clickable { model.setAppearance(appearance); sheet = Sheet.None }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = state.appearance == appearance, onClick = { model.setAppearance(appearance); sheet = Sheet.None })
            Text(if (appearance == Appearance.System) "Follow system" else appearance.name)
          }
        } } }, confirmButton = { TextButton(onClick = { sheet = Sheet.None }) { Text("Done") } })
      Sheet.Logs -> {
        val log by produceState("Loading logs…", state.selectedName) { value = model.log() }
        AlertDialog(onDismissRequest = { sheet = Sheet.None }, title = { Text("Connection log") },
          text = { SelectionContainer { Text(log, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 11.sp) } },
          confirmButton = { TextButton(onClick = { sheet = Sheet.None }) { Text("Done") } },
          dismissButton = { TextButton(onClick = { copy(log) }) { Text("Copy log") } })
      }
      Sheet.Unlock -> {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { sheet = Sheet.None }, title = { Text("Unlock your key") },
          text = { OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
          confirmButton = { TextButton(onClick = { sheet = Sheet.None; onConnect(passphrase) }) { Text("Connect") } },
          dismissButton = { TextButton(onClick = { sheet = Sheet.None }) { Text("Cancel") } })
      }
    }

    selectedPeer?.let { peer ->
      AlertDialog(onDismissRequest = { selectedPeer = null }, title = { Text(peer.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(peer.transport, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
          if (peer.address.isNotEmpty()) Text(peer.address, fontFamily = FontFamily.Monospace)
          if (peer.latency.isNotEmpty()) Text("Round trip · ${peer.latency}")
          if (peer.details.isNotEmpty()) SelectionContainer { Text(peer.details, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
          else Text("Connect to the network to see live reachability and transport details.")
        } }, confirmButton = { TextButton(onClick = { selectedPeer = null }) { Text("Done") } },
        dismissButton = { if (peer.address.isNotEmpty()) TextButton(onClick = { copy(peer.address.substringBefore('/')) }) { Text("Copy address") } })
    }
    state.error?.let { error ->
      AlertDialog(onDismissRequest = model::dismissError, icon = { Icon(Icons.Outlined.Info, null) },
        title = { Text("Couldn't complete that") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("Got it") } })
    }
  }
}

@Composable
private fun HomeScreen(state: MeshState, connect: () -> Unit, networks: () -> Unit, devices: () -> Unit,
  add: () -> Unit, addPeer: () -> Unit, peerClick: (MeshPeer) -> Unit, copy: (String) -> Unit) {
  val profile = state.profile
  LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
    if (profile == null) {
      item {
        Column(Modifier.padding(top = 30.dp)) {
          Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
            MeshMark(Modifier.padding(23.dp).size(46.dp), MaterialTheme.colorScheme.primary)
          }
          Text("Your devices.\nOne private network.", Modifier.padding(top = 26.dp), fontSize = 31.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
          Text("Connect your phone to the devices you trust, wherever you are.", Modifier.padding(top = 14.dp), fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Button(onClick = add, Modifier.fillMaxWidth().padding(top = 28.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add your first network")
          }
        }
      }
      item {
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
          FeatureRow(Icons.Outlined.Hub, "A mesh of your own", "Devices connect directly whenever they can.")
          FeatureRow(Icons.Outlined.Shield, "Private by design", "Encrypted connections. You control the keys.")
          FeatureRow(Icons.Outlined.PhoneAndroid, "Works without root", "Uses Android's built-in VPN permission.")
        }
      }
    } else {
      item {
        Row(Modifier.fillMaxWidth().clickable(onClick = networks), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Eyebrow("YOUR NETWORK")
            Text(profile.name, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
          }
          Icon(Icons.Outlined.UnfoldMore, "Choose a network", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      item { ConnectionPanel(state, connect) }
      item {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
          Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
              Text("VPN address", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(profile.address ?: "Not configured", Modifier.padding(top = 3.dp), fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            }
            profile.address?.let { address -> IconButton(onClick = { copy(address) }) { Icon(Icons.Outlined.ContentCopy, "Copy VPN address", Modifier.size(18.dp)) } }
          }
        }
      }
      item { SectionHeading("Devices", if (state.peers.isEmpty()) "Add peer" else "View all", if (state.peers.isEmpty()) addPeer else devices) }
      if (state.peers.isEmpty()) item {
        EmptySection(Icons.Outlined.Devices, "Make your first connection", "Add another device's public configuration to bring it into your mesh.", "Add a peer", addPeer)
      } else {
        items(state.peers.take(4), key = { it.name }) { peer -> PeerRow(peer) { peerClick(peer) } }
        if (state.phase == ConnectionPhase.Offline) item {
          Text("Connect to see live device status.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
      }
      profile.problem?.let { problem -> item { Text(problem, color = MaterialTheme.colorScheme.error) } }
    }
  }
}

@Composable
private fun ConnectionPanel(state: MeshState, onClick: () -> Unit) {
  val active = state.phase == ConnectionPhase.Active
  val transitioning = state.phase == ConnectionPhase.Starting || state.phase == ConnectionPhase.Stopping
  val title = when (state.phase) {
    ConnectionPhase.Active -> if (state.onlinePeers > 0) "You're connected." else "VPN is active."
    ConnectionPhase.Starting -> "Connecting…"
    ConnectionPhase.Stopping -> "Disconnecting…"
    ConnectionPhase.Offline -> "Ready to connect."
  }
  val explanation = when (state.phase) {
    ConnectionPhase.Active -> if (state.onlinePeers > 0) "${state.onlinePeers} ${if (state.onlinePeers == 1) "peer is" else "peers are"} reachable in your mesh." else "Waiting for a reachable peer."
    ConnectionPhase.Starting -> "Starting your private connection."
    ConnectionPhase.Stopping -> "Closing the VPN connection."
    ConnectionPhase.Offline -> "Your private network is one tap away."
  }
  Surface(color = Color(0xFF1C2436), shape = RoundedCornerShape(25.dp)) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        MeshMark(Modifier.size(35.dp), if (active) SignalGreen else Color(0xFF99A8C5))
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.Shield, null, tint = Color(0xFF8492AB), modifier = Modifier.size(20.dp))
      }
      Text(title, Modifier.padding(top = 24.dp), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
      Text(explanation, Modifier.padding(top = 7.dp), color = Color(0xFFB4BFD3), fontSize = 14.sp, lineHeight = 21.sp)
      Button(onClick = onClick, enabled = !transitioning && !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0xFF33405A) else Color(0xFFB9C7FF),
          contentColor = if (active) Color.White else Color(0xFF182956))) {
        if (transitioning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFB9C7FF))
        else Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(if (active) "Disconnect" else if (transitioning) "Please wait" else "Connect", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun DevicesScreen(state: MeshState, addPeer: () -> Unit, peerClick: (MeshPeer) -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item { ScreenTitle("Devices", "Your devices, connected.", Icons.Outlined.Add, "Add a peer", addPeer, state.profile != null) }
    if (state.profile == null) item { EmptySection(Icons.Outlined.Hub, "No network yet", "Add a network from Home to start connecting your devices.") }
    else {
      item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search devices") },
        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(21.dp)) }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
      if (query.isEmpty()) item {
        Eyebrow("THIS DEVICE")
        Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(18.dp)) {
          Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceIcon(Icons.Outlined.PhoneAndroid)
            Column(Modifier.padding(start = 14.dp)) {
              Text(state.profile!!.nodeName, fontWeight = FontWeight.Medium)
              Text(state.profile!!.address ?: "Address not configured", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
      item { SectionHeading("Peers", "${state.onlinePeers} online") }
      val filtered = state.peers.filter { it.name.contains(query, true) || it.address.contains(query, true) }
      if (filtered.isEmpty()) item {
        EmptySection(Icons.Outlined.Devices, if (query.isNotEmpty()) "No matching devices" else "Your mesh starts here",
          if (query.isNotEmpty()) "Try another name or address." else "Add a peer to connect this phone to another device.",
          if (query.isEmpty()) "Add a peer" else null, addPeer)
      }
      items(filtered, key = { it.name }) { peer -> PeerRow(peer) { peerClick(peer) } }
    }
  }
}

@Composable
private fun NetworksScreen(state: MeshState, select: (String) -> Unit, add: () -> Unit, settings: () -> Unit, share: () -> Unit) {
  LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
    item { ScreenTitle("Networks", "Your private spaces.", Icons.Outlined.Add, "Add a network", add) }
    if (state.profiles.isEmpty()) item { EmptySection(Icons.Outlined.Hub, "A space for your devices", "Join an existing tinc network or create your own.", "Add a network", add) }
    items(state.profiles, key = { it.name }) { profile ->
      Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
          Row(Modifier.fillMaxWidth().clickable { select(profile.name) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceIcon(Icons.Outlined.Hub)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
              Text(profile.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
              Text(profile.address ?: "Configure an address", Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (profile.name == state.selectedName) Icon(Icons.Outlined.CheckCircle, "Selected network", tint = MaterialTheme.colorScheme.primary)
          }
          if (profile.name == state.selectedName) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
              TextButton(onClick = settings) { Icon(Icons.Outlined.Tune, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Settings") }
              TextButton(onClick = share, enabled = !state.busy) { Icon(Icons.Outlined.Share, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Share public key") }
            }
          }
        }
      }
    }
    item { Text("One network at a time. You choose what traffic goes through it.", fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
private fun SettingsScreen(state: MeshState, open: (Sheet) -> Unit, notifications: () -> Unit) {
  LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
    item { ScreenTitle("Settings", "Network and app preferences.") }
    state.profile?.let { profile -> item {
      Eyebrow("NETWORK · ${profile.name.uppercase()}")
      Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(20.dp)) {
        Column {
          ActionRow(Icons.Outlined.Route, "Addresses & routes", profile.configuration.routes.joinToString { it.toString() }.ifEmpty { "No VPN routes" }, { open(Sheet.NetworkSettings) })
          HorizontalDivider(Modifier.padding(horizontal = 18.dp))
          ActionRow(Icons.Outlined.Dns, "DNS", profile.configuration.dnsServers.joinToString().ifEmpty { "Use network defaults" }, { open(Sheet.NetworkSettings) })
          HorizontalDivider(Modifier.padding(horizontal = 18.dp))
          ActionRow(Icons.Outlined.Apps, "App access", if (profile.configuration.disallowedApplications.isEmpty()) "All apps" else "${profile.configuration.disallowedApplications.size} apps bypass the VPN", { open(Sheet.NetworkSettings) })
        }
      }
    } }
    item {
      Eyebrow("PREFERENCES")
      Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(20.dp)) {
        Column {
          ActionRow(Icons.Outlined.Palette, "Appearance", if (state.appearance == Appearance.System) "Follow system" else state.appearance.name, { open(Sheet.Appearance) })
          HorizontalDivider(Modifier.padding(horizontal = 18.dp))
          ActionRow(Icons.Outlined.Notifications, "Connection notifications", "VPN status and disconnect control", notifications)
        }
      }
    }
    item {
      Eyebrow("DIAGNOSTICS")
      Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(20.dp)) {
        ActionRow(Icons.Outlined.Terminal, "Connection log", "See what's happening under the hood", { open(Sheet.Logs) })
      }
    }
    item {
      Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("tinc · ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Text("Upstream tinc 1.1 · 211e3df\nRootless Android client · GPL-3.0-or-later", fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun PeerRow(peer: MeshPeer, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box {
        DeviceIcon(if (peer.name.contains("mac", true)) Icons.Outlined.LaptopMac else Icons.Outlined.Computer)
        if (peer.reachable) Box(Modifier.align(Alignment.BottomEnd).size(10.dp).background(Color(0xFF269977), CircleShape))
      }
      Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
        Text(peer.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Text(peer.transport, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (peer.latency.isNotEmpty()) Text(peer.latency, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
  }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String, icon: ImageVector? = null, description: String = "", action: () -> Unit = {}, enabled: Boolean = true) {
  Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
      Text(subtitle, Modifier.padding(top = 6.dp), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (icon != null) FilledTonalIconButton(onClick = action, enabled = enabled) { Icon(icon, description) }
  }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    if (onClick != null) TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(action, fontSize = 13.sp) }
    else Text(action, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun Eyebrow(text: String) = Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun DeviceIcon(icon: ImageVector) {
  Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
    Icon(icon, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, detail: String, action: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.weight(1f).padding(horizontal = 15.dp)) {
      Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
      Text(detail, Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
  }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String) {
  Row(verticalAlignment = Alignment.Top) {
    Icon(icon, null, Modifier.padding(top = 2.dp).size(23.dp), tint = MaterialTheme.colorScheme.primary)
    Column(Modifier.padding(start = 16.dp)) {
      Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
      Text(detail, Modifier.padding(top = 5.dp), fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun EmptySection(icon: ImageVector, title: String, detail: String, action: String? = null, onClick: () -> Unit = {}) {
  Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.outline)
    Text(title, Modifier.padding(top = 14.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Text(detail, Modifier.padding(top = 7.dp), fontSize = 13.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (action != null) TextButton(onClick = onClick, Modifier.padding(top = 7.dp)) { Text(action) }
  }
}

@Composable
private fun MeshMark(modifier: Modifier, color: Color) {
  Canvas(modifier) {
    val radius = size.minDimension * .105f
    val points = listOf(Offset(size.width * .5f, size.height * .14f), Offset(size.width * .14f, size.height * .79f), Offset(size.width * .86f, size.height * .79f))
    for (i in points.indices) drawLine(color.copy(alpha = .55f), points[i], points[(i + 1) % 3], size.minDimension * .055f, StrokeCap.Round)
    points.forEach { drawCircle(color, radius * 1.45f, it, style = Stroke(size.minDimension * .045f)) }
    drawCircle(color, radius * .45f, Offset(size.width * .5f, size.height * .56f))
  }
}

@Composable
private fun CreateNetworkDialog(busy: Boolean, dismiss: () -> Unit, create: (String, String, String, String) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var node by rememberSaveable { mutableStateOf("android_phone") }
  var address by rememberSaveable { mutableStateOf("10.77.0.3/32") }
  var routes by rememberSaveable { mutableStateOf("10.77.0.0/24") }
  FormDialog("Create a network", "Your keys are generated on this device. Add peers after creating the network.", busy, "Create network", dismiss,
    { create(name.trim(), node.trim(), address, routes) }) {
    FormField(name, { name = it }, "Network name", "home")
    FormField(node, { node = it }, "Device name", "android_phone")
    FormField(address, { address = it }, "VPN address", "10.77.0.3/32")
    FormField(routes, { routes = it }, "Traffic routes", "10.77.0.0/24")
  }
}

@Composable
private fun JoinNetworkDialog(busy: Boolean, scanned: String?, consumeScan: () -> Unit, dismiss: () -> Unit, scan: () -> Unit, join: (String, String) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var invitation by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(scanned) { scanned?.let { invitation = it; consumeScan() } }
  FormDialog("Join a network", "Ask someone already on the network for a tinc invitation.", busy, "Join network", dismiss,
    { join(name.trim(), invitation) }) {
    FormField(name, { name = it }, "Save network as", "home")
    FormField(invitation, { invitation = it }, "Invitation", "Paste invitation", lines = 3)
    OutlinedButton(onClick = scan, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Scan QR code")
    }
  }
}

@Composable
private fun AddPeerDialog(busy: Boolean, dismiss: () -> Unit, add: (String, String) -> Unit) {
  var configuration by rememberSaveable { mutableStateOf("") }
  var endpoint by rememberSaveable { mutableStateOf("") }
  FormDialog("Add a peer", "Paste the other device's public configuration. Share your public key with that device too.", busy, "Add peer", dismiss,
    { add(configuration, endpoint) }) {
    FormField(configuration, { configuration = it }, "Public peer configuration", "Name = my_peer\nEd25519PublicKey = …", lines = 5)
    FormField(endpoint, { endpoint = it }, "Address override (optional)", "IP address or hostname")
  }
}

@Composable
private fun NetworkSettingsDialog(profile: MeshProfile, busy: Boolean, dismiss: () -> Unit, save: (String, String, String, List<String>) -> Unit) {
  var addresses by rememberSaveable { mutableStateOf(profile.configuration.addresses.joinToString("\n")) }
  var routes by rememberSaveable { mutableStateOf(profile.configuration.routes.joinToString("\n")) }
  var dns by rememberSaveable { mutableStateOf(profile.configuration.dnsServers.joinToString("\n")) }
  var bypass by rememberSaveable { mutableStateOf(profile.configuration.disallowedApplications) }
  var appPicker by rememberSaveable { mutableStateOf(false) }
  FormDialog("Network settings", "Only traffic matching these routes uses the VPN. Reconnect to apply changes.", busy, "Save", dismiss,
    { save(addresses, routes, dns, bypass) }) {
    FormField(addresses, { addresses = it }, "VPN addresses", "10.77.0.3/32", 2)
    FormField(routes, { routes = it }, "Traffic routes", "10.77.0.0/24", 2)
    FormField(dns, { dns = it }, "DNS servers (optional)", "Use network defaults", 2)
    OutlinedButton(onClick = { appPicker = true }, Modifier.fillMaxWidth()) { Text(if (bypass.isEmpty()) "App access · All apps" else "${bypass.size} apps bypass the VPN") }
  }
  if (appPicker) AppPicker(bypass, { appPicker = false }) { bypass = it; appPicker = false }
}

@Composable
private fun AppPicker(initial: List<String>, dismiss: () -> Unit, save: (List<String>) -> Unit) {
  val context = LocalContext.current
  var selected by remember { mutableStateOf(initial.toSet()) }
  val apps by produceState<List<Pair<String, String>>>(emptyList()) {
    value = withContext(Dispatchers.IO) {
      context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .filter { it.activityInfo.packageName != BuildConfig.APPLICATION_ID }
        .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
  }
  AlertDialog(onDismissRequest = dismiss, title = { Text("Apps that bypass the VPN") },
    text = { LazyColumn(Modifier.heightIn(max = 380.dp)) {
      item { Text("Selected apps use their normal network connection.", Modifier.padding(bottom = 12.dp), fontSize = 13.sp) }
      items(apps, key = { it.first }) { (pkg, label) ->
        Row(Modifier.fillMaxWidth().clickable { selected = if (pkg in selected) selected - pkg else selected + pkg }, verticalAlignment = Alignment.CenterVertically) {
          Checkbox(pkg in selected, { checked -> selected = if (checked) selected + pkg else selected - pkg })
          Text(label, fontSize = 14.sp)
        }
      }
    } }, confirmButton = { TextButton(onClick = { save(selected.toList()) }) { Text("Done") } },
    dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun FormDialog(title: String, detail: String, busy: Boolean, action: String, dismiss: () -> Unit, submit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(title) },
    text = { Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
      Text(detail, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      content()
    } },
    confirmButton = { Button(onClick = submit, enabled = !busy) {
      if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
      else Text(action)
    } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun FormField(value: String, change: (String) -> Unit, label: String, placeholder: String, lines: Int = 1) {
  OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder) },
    singleLine = lines == 1, minLines = if (lines > 1) lines else 1, maxLines = if (lines > 1) lines + 2 else 1,
    shape = RoundedCornerShape(12.dp))
}
