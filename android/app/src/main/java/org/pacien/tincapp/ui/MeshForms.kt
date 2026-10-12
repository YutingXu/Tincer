// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pacien.tincapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun Field(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  placeholder: String? = null,
  supporting: String? = null,
  lines: Int = 1,
  multiline: Boolean = lines > 1,
  code: Boolean = false,
  keyboard: KeyboardType = KeyboardType.Ascii,
  trailing: (@Composable () -> Unit)? = null,
) {
  val style = if (code) MaterialTheme.typography.bodyMedium.merge(Code) else MaterialTheme.typography.bodyLarge.merge(Figures)
  OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, textStyle = style,
    placeholder = placeholder?.let { { Text(it, style = style) } }, supportingText = supporting?.let { { Text(it) } },
    trailingIcon = trailing, singleLine = !multiline, minLines = lines, maxLines = if (multiline) lines + 3 else 1,
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = keyboard,
      imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
  )
}

@Composable
internal fun CreateNetworkSheet(busy: Boolean, onDismiss: () -> Unit, onCreate: (String, String, String, String) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var node by rememberSaveable { mutableStateOf("android_phone") }
  var address by rememberSaveable { mutableStateOf("10.77.0.3/32") }
  var routes by rememberSaveable { mutableStateOf("10.77.0.0/24") }
  MeshSheet("Create network", onDismiss, subtitle = "Keys are generated on this device.", dismissible = !busy,
    footer = { PrimaryButton("Create", { onCreate(name.trim(), node.trim(), address, routes) }, enabled = name.isNotBlank(), busy = busy) }) {
    Field(name, { name = it }, "Network name", "home")
    Field(node, { node = it }, "Device name", "android_phone")
    Field(address, { address = it }, "Address", "10.77.0.3/32", supporting = "This device's address on the network")
    Field(routes, { routes = it }, "Routes", "10.77.0.0/24", supporting = "Subnets to send through the VPN")
  }
}

@Composable
internal fun JoinNetworkSheet(busy: Boolean, scanned: String?, onScanConsumed: () -> Unit, onScan: () -> Unit, onDismiss: () -> Unit,
  onJoin: (String, String) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var invitation by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(scanned) { scanned?.let { invitation = it; onScanConsumed() } }
  MeshSheet("Join network", onDismiss, subtitle = "Use an invitation from a node already on the network.", dismissible = !busy,
    footer = { PrimaryButton("Join", { onJoin(name.trim(), invitation) }, enabled = name.isNotBlank() && invitation.isNotBlank(), busy = busy) }) {
    Field(name, { name = it }, "Network name", "home")
    Field(invitation, { invitation = it }, "Invitation", "host:port/key", lines = 3, code = true,
      trailing = { IconButton(onClick = onScan, enabled = !busy) { Icon(Icons.Outlined.QrCodeScanner, "Scan QR code") } })
  }
}

@Composable
internal fun AddPeerSheet(busy: Boolean, onShareKey: () -> Unit, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
  var configuration by rememberSaveable { mutableStateOf("") }
  var endpoint by rememberSaveable { mutableStateOf("") }
  MeshSheet("Add peer", onDismiss, subtitle = "Both nodes need each other's public key.", dismissible = !busy,
    footer = { PrimaryButton("Add peer", { onAdd(configuration, endpoint) }, enabled = configuration.isNotBlank(), busy = busy) }) {
    Group {
      item { ListRow("Your public key", supporting = "Send it to the other node", onClick = onShareKey, enabled = !busy,
        leading = { Icon(Icons.Outlined.Share, null) }) }
    }
    Field(configuration, { configuration = it }, "Their public key", "Name = laptop\nEd25519PublicKey = …", lines = 4, code = true,
      supporting = "Output of tinc export on the other node")
    Field(endpoint, { endpoint = it }, "Address (optional)", "203.0.113.5 or host.example", keyboard = KeyboardType.Uri,
      supporting = "Overrides the address in their export")
  }
}

@Composable
internal fun NetworkSettingsSheet(profile: MeshProfile, busy: Boolean, loadApps: suspend () -> List<InstalledApp>, onDismiss: () -> Unit,
  onSave: (String, String, String, List<String>) -> Unit) {
  val configuration = profile.configuration
  var addresses by rememberSaveable { mutableStateOf(configuration.addresses.joinToString("\n")) }
  var routes by rememberSaveable { mutableStateOf(configuration.routes.joinToString("\n")) }
  var dns by rememberSaveable { mutableStateOf(configuration.dnsServers.joinToString("\n")) }
  var excluded by rememberSaveable { mutableStateOf(configuration.disallowedApplications) }
  var picking by rememberSaveable { mutableStateOf(false) }
  if (picking) {
    AppPickerSheet(excluded, loadApps, onBack = { picking = false }) { excluded = it; picking = false }
    return
  }
  MeshSheet("Network settings", onDismiss, subtitle = "Changes apply the next time you connect.", dismissible = !busy,
    footer = { PrimaryButton("Save", { onSave(addresses, routes, dns, excluded) }, busy = busy) }) {
    Field(addresses, { addresses = it }, "Addresses", "10.77.0.3/32", multiline = true, supporting = "One per line, with prefix")
    Field(routes, { routes = it }, "Routes", "10.77.0.0/24", multiline = true, supporting = "Subnets to send through the VPN")
    Field(dns, { dns = it }, "DNS servers", "Default", multiline = true)
    Group {
      item { ListRow("Excluded apps", supporting = if (excluded.isEmpty()) "None" else "${excluded.size} app${if (excluded.size == 1) "" else "s"}",
        onClick = { picking = true }, leading = { Icon(Icons.Outlined.Apps, null) }) }
    }
  }
}

@Composable
private fun AppPickerSheet(initial: List<String>, loadApps: suspend () -> List<InstalledApp>, onBack: () -> Unit, onDone: (List<String>) -> Unit) {
  var selected by rememberSaveable { mutableStateOf(initial.toSet()) }
  var query by rememberSaveable { mutableStateOf("") }
  val apps by produceState<List<InstalledApp>?>(null) { value = loadApps() }
  MeshSheet("Excluded apps", onBack, subtitle = "These apps bypass the VPN.",
    headerAction = { TextButton(onClick = { onDone(selected.toList()) }) { Text("Done", style = MaterialTheme.typography.labelLarge) } }) {
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, singleLine = true,
      leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = CircleShape)
    val list = apps
    if (list == null) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Spinner() }
    else {
      // Selected apps first, so the current choice is visible without scrolling.
      val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) }
        .sortedByDescending { it.packageName in initial }
      LazyColumn(Modifier.heightIn(max = 440.dp)) {
        itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
          val checked = app.packageName in selected
          val toggle = { selected = if (checked) selected - app.packageName else selected + app.packageName }
          Segment(index, shown.size, Modifier.animateItem().padding(bottom = 2.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically) {
              val icon = app.icon
              if (icon != null) Image(icon, null, Modifier.size(36.dp).clip(CircleShape))
              else Avatar(app.label, null, size = 36.dp)
              Text(app.label, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
              Switch(checked, { toggle() })
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun UnlockDialog(onDismiss: () -> Unit, onUnlock: (String) -> Unit) {
  var passphrase by remember { mutableStateOf("") }
  AlertDialog(onDismissRequest = onDismiss, title = { Text("Unlock key") },
    text = { OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(),
      singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)) },
    confirmButton = { TextButton(onClick = { onUnlock(passphrase) }, enabled = passphrase.isNotEmpty()) { Text("Connect") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun ErrorDialog(message: String, onDismiss: () -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.ErrorOutline, null) }, title = { Text("Couldn’t finish") }, text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } })
}
