// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import org.pacien.tincapp.data.VpnInterfaceConfiguration

data class MeshProfile(
  val name: String,
  val nodeName: String,
  val configuration: VpnInterfaceConfiguration,
  val peers: List<String>,
  val problem: String? = null,
) {
  val address get() = configuration.addresses.firstOrNull()?.address
}

enum class ConnectionPhase { Offline, Starting, Active, Stopping }
enum class Appearance { System, Light, Dark }

data class MeshPeer(
  val name: String,
  val address: String = "",
  val transport: String = "Saved peer",
  val reachable: Boolean = false,
  val latency: String = "",
  val details: String = "",
) {
  companion object {
    fun fromInfo(name: String, info: String): MeshPeer {
      val fields = info.lineSequence().filter { ':' in it }
        .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
      val path = fields["Reachability"].orEmpty()
      val reachable = path.isNotEmpty() && path != "unreachable" && path != "unknown"
      val transport = when {
        path == "directly with UDP" -> "Direct UDP"
        path == "directly with TCP" -> "Direct TCP"
        path.startsWith("indirectly via ") -> "Via ${path.removePrefix("indirectly via ")}"
        path.startsWith("none, forwarded via ") -> "Via ${path.removePrefix("none, forwarded via ")}"
        path == "unreachable" -> "Offline"
        path == "unknown" -> "Establishing connection"
        path.isEmpty() -> "Checking connection"
        else -> path.replaceFirstChar { it.uppercase() }
      }
      return MeshPeer(name, fields["Subnets"].orEmpty().substringBefore(' '), transport, reachable,
        fields["RTT"]?.toDoubleOrNull()?.let { "%.1f ms".format(java.util.Locale.ROOT, it) }.orEmpty(), info)
    }
  }
}

data class MeshState(
  val profiles: List<MeshProfile> = emptyList(),
  val selectedName: String? = null,
  val phase: ConnectionPhase = ConnectionPhase.Offline,
  val peers: List<MeshPeer> = emptyList(),
  val appearance: Appearance = Appearance.System,
  val busy: Boolean = false,
  val error: String? = null,
  val notice: String? = null,
  val scannedInvitation: String? = null,
) {
  val profile get() = profiles.firstOrNull { it.name == selectedName }
  val onlinePeers get() = peers.count { it.reachable }
}
