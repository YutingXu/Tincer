// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pacien.tincapp.commands.Tinc
import org.pacien.tincapp.commands.TincApp
import org.pacien.tincapp.context.App
import org.pacien.tincapp.context.AppPaths
import org.pacien.tincapp.data.CidrAddress
import org.pacien.tincapp.data.VpnInterfaceConfiguration
import org.pacien.tincapp.intent.Actions
import org.pacien.tincapp.service.TincVpnService
import java.net.InetAddress

class MeshViewModel(application: Application) : AndroidViewModel(application) {
  internal val page = mutableStateOf(Page.Home)
  internal val sheet = mutableStateOf(Sheet.None)
  internal val peer = mutableStateOf<String?>(null)
  private val preferences = application.getSharedPreferences("mesh_ui", Context.MODE_PRIVATE)
  private val mutableState = MutableStateFlow(MeshState(
    selectedName = preferences.getString("network", null),
    appearance = Appearance.entries.firstOrNull { it.name == preferences.getString("appearance", null) }
      ?: Appearance.System))
  val state = mutableState.asStateFlow()
  var pendingPassphrase: String? = null
  private var polling: Job? = null
  private val broadcasts = LocalBroadcastManager.getInstance(application)
  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      mutableState.update { it.copy(phase = if (TincVpnService.isConnected()) ConnectionPhase.Active else ConnectionPhase.Offline) }
      viewModelScope.launch { refresh() }
    }
  }

  init {
    broadcasts.registerReceiver(receiver, IntentFilter().apply {
      addAction(Actions.EVENT_CONNECTED)
      addAction(Actions.EVENT_DISCONNECTED)
      addAction(Actions.EVENT_ABORTED)
    })
    viewModelScope.launch { refresh() }
  }

  fun observe(visible: Boolean) {
    polling?.cancel()
    if (visible) polling = viewModelScope.launch {
      while (true) { refresh(); delay(3000) }
    }
  }

  private suspend fun refresh() {
    val profiles = withContext(Dispatchers.IO) {
      AppPaths.confDir().listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.map { folder ->
        val node = AppPaths.tincConfFile(folder.name).takeIf { it.exists() }?.readLines()
          ?.firstOrNull { it.substringBefore('=').trim().equals("Name", true) }?.substringAfter('=')?.trim().orEmpty()
        val peers = AppPaths.hostsDir(folder.name).listFiles().orEmpty()
          .filter { it.isFile && it.name != node && !it.name.endsWith("-up") && !it.name.endsWith("-down") }.map { it.name }.sorted()
        try {
          MeshProfile(folder.name, node, VpnInterfaceConfiguration.fromIfaceConfiguration(AppPaths.netConfFile(folder.name)), peers)
        } catch (exception: Exception) {
          MeshProfile(folder.name, node, VpnInterfaceConfiguration(), peers, exception.message ?: "Network settings are missing.")
        }
      }
    }
    val active = TincVpnService.isConnected()
    val current = TincVpnService.getCurrentNetName().takeIf { active }
    val selected = current ?: mutableState.value.selectedName?.takeIf { name -> profiles.any { it.name == name } } ?: profiles.firstOrNull()?.name
    val profile = profiles.firstOrNull { it.name == selected }
    val peers = if (active && selected != null) {
      try {
        val discovered = Tinc.dumpNodes(selected).await().map { it.substringBefore(" id ") }.filter { it != profile?.nodeName }
        (discovered + profile?.peers.orEmpty()).distinct().sorted().map { name ->
          try { MeshPeer.fromInfo(name, Tinc.info(selected, name).await()) }
          catch (exception: Exception) { MeshPeer(name, transport = "Offline") }
        }
      } catch (exception: Exception) {
        // A disconnect can close the control socket while its status is being read.
        profile?.peers.orEmpty().map { MeshPeer(it, transport = if (TincVpnService.isConnected()) "Checking connection" else "Saved peer") }
      }
    } else profile?.peers.orEmpty().map { MeshPeer(it) }
    val notification = App.notificationManager.getError()
    mutableState.update { old -> old.copy(profiles = profiles, selectedName = selected, peers = peers,
      phase = if (active) ConnectionPhase.Active else if (old.phase in listOf(ConnectionPhase.Starting, ConnectionPhase.Stopping) && notification == null) old.phase else ConnectionPhase.Offline,
      error = notification?.message ?: old.error) }
  }

  fun select(name: String) {
    if (TincVpnService.isConnected() && name != state.value.selectedName) {
      mutableState.update { it.copy(notice = "Disconnect before switching networks.") }
      return
    }
    preferences.edit().putString("network", name).apply()
    mutableState.update { it.copy(selectedName = name) }
    viewModelScope.launch { refresh() }
  }

  fun setAppearance(value: Appearance) {
    preferences.edit().putString("appearance", value.name).apply()
    mutableState.update { it.copy(appearance = value) }
  }

  fun connect(passphrase: String? = null) {
    val network = state.value.selectedName ?: return
    App.notificationManager.dismissAll()
    mutableState.update { it.copy(phase = ConnectionPhase.Starting, error = null) }
    TincVpnService.connect(network, passphrase)
  }

  fun disconnect() {
    mutableState.update { it.copy(phase = ConnectionPhase.Stopping) }
    TincVpnService.disconnect()
  }

  fun permissionDenied() { mutableState.update { it.copy(error = "Tincer needs VPN permission to connect.", phase = ConnectionPhase.Offline) } }
  fun dismissError() { App.notificationManager.dismissAll(); mutableState.update { it.copy(error = null) } }
  fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
  fun scanned(invitation: String?) { mutableState.update { it.copy(scannedInvitation = invitation?.removePrefix("tinc:")) } }

  fun create(name: String, node: String, address: String, routes: String, done: () -> Unit) = action(done) {
    require(name.matches(Regex("[A-Za-z0-9_-]+"))) { "Use letters, numbers, underscores or hyphens for the network name." }
    require(node.matches(Regex("[A-Za-z0-9_]+"))) { "Use letters, numbers or underscores for the device name." }
    require(!AppPaths.confDir(name).exists()) { "A network named $name already exists." }
    val addresses = parseCidrs(address)
    require(addresses.isNotEmpty()) { "Enter a VPN address with its prefix, such as 10.77.0.3/32." }
    val configuration = VpnInterfaceConfiguration(addresses = addresses, routes = parseCidrs(routes), mtu = 1400)
    Tinc.init(name, node).await()
    configuration.write(AppPaths.netConfFile(name))
    saveSubnets(name, addresses)
    TincApp.removeScripts(name).await()
    if (!TincVpnService.isConnected()) preferences.edit().putString("network", name).apply().also {
      mutableState.update { it.copy(selectedName = name) }
    }
  }

  fun join(name: String, invitation: String, done: () -> Unit) = action(done) {
    require(name.matches(Regex("[A-Za-z0-9_-]+"))) { "Enter a network name using letters, numbers, underscores or hyphens." }
    require(!AppPaths.confDir(name).exists()) { "A network named $name already exists." }
    require(invitation.isNotBlank()) { "Paste or scan an invitation." }
    Tinc.join(name, invitation.trim()).await()
    TincApp.generateIfaceCfg(name).await()
    TincApp.removeScripts(name).await()
    if (!TincVpnService.isConnected()) {
      preferences.edit().putString("network", name).apply()
      mutableState.update { it.copy(selectedName = name) }
    }
  }

  fun addPeer(publicConfiguration: String, endpoint: String, done: () -> Unit) = action(done) {
    val profile = state.value.profile ?: error("Choose a network first.")
    val name = publicConfiguration.lineSequence().firstOrNull { it.trim().startsWith("Name =") }
      ?.substringAfter('=' )?.trim() ?: error("Paste the other node's public key, from tinc export.")
    require(name != profile.nodeName) { "That is this device's own key. Paste the other node's key." }
    Tinc.importPeer(profile.name, publicConfiguration.trim()).await()
    if (endpoint.isNotBlank()) Tinc.set(profile.name, "$name.Address", endpoint.trim()).await()
    Tinc.add(profile.name, "ConnectTo", name).await()
    if (TincVpnService.isConnected()) { Tinc.reload(profile.name).await(); Tinc.retry(profile.name).await() }
  }

  fun saveSettings(addresses: String, routes: String, dns: String, bypassApps: List<String>, done: () -> Unit) = action(done) {
    val profile = state.value.profile ?: error("Choose a network first.")
    val ips = parseCidrs(addresses)
    require(ips.isNotEmpty()) { "Enter at least one VPN address." }
    val servers = dns.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }
    servers.forEach { require(it.matches(Regex("[0-9a-fA-F:.]+"))) { "DNS servers must be IP addresses." }; InetAddress.getByName(it) }
    profile.configuration.copy(addresses = ips, routes = parseCidrs(routes), dnsServers = servers,
      allowedApplications = emptyList(), disallowedApplications = bypassApps).write(AppPaths.netConfFile(profile.name))
    saveSubnets(profile.name, ips)
    mutableState.update { it.copy(notice = if (TincVpnService.isConnected()) "Saved. Reconnect to apply." else "Saved.") }
  }

  fun exportPublic(done: (String) -> Unit) = action {
    val profile = state.value.profile ?: error("Choose a network first.")
    val publicConfiguration = Tinc.export(profile.name).await()
    withContext(Dispatchers.Main) { done(publicConfiguration) }
  }

  suspend fun log(): String = withContext(Dispatchers.IO) {
    state.value.selectedName?.let { AppPaths.logFile(it) }?.takeIf { it.exists() }
      ?.readLines()?.takeLast(150)?.joinToString("\n") ?: "No log yet. Connect to start one."
  }

  private suspend fun saveSubnets(network: String, addresses: List<CidrAddress>) {
    addresses.forEachIndexed { index, ip ->
      val subnet = "${ip.address}/${if (':' in ip.address) 128 else 32}"
      if (index == 0) Tinc.set(network, "Subnet", subnet).await() else Tinc.add(network, "Subnet", subnet).await()
    }
  }

  private fun action(done: () -> Unit = {}, work: suspend () -> Unit) {
    if (state.value.busy) return
    mutableState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      try { withContext(Dispatchers.IO) { work() }; refresh(); done() }
      catch (exception: Exception) { mutableState.update { it.copy(error = exception.cause?.message ?: exception.message ?: "The operation failed.") } }
      finally { mutableState.update { it.copy(busy = false) } }
    }
  }

  override fun onCleared() {
    polling?.cancel()
    broadcasts.unregisterReceiver(receiver)
    super.onCleared()
  }

  companion object {
    fun parseCidrs(input: String): List<CidrAddress> = input.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { value ->
      require('/' in value && value.substringBefore('/').matches(Regex("[0-9a-fA-F:.]+"))) { "Use IP addresses with prefixes, such as 10.77.0.3/32." }
      val cidr = CidrAddress.fromSlashSeparated(value)
      val size = InetAddress.getByName(cidr.address).address.size * 8
      require(cidr.prefix in 0..size) { "The prefix for ${cidr.address} must be between 0 and $size." }
      cidr
    }
  }
}

private suspend fun <T> CompletableFuture<T>.await(): T = withContext(Dispatchers.IO) { get() }
