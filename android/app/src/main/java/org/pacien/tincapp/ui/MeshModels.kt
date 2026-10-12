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

    /** Turns `tinc info` output into labelled rows, leaving out what the sheet already shows. */
    fun details(info: String): List<Pair<String, String>> {
      val fields = info.lineSequence().filter { ':' in it }
        .map { it.substringBefore(':').trim() to it.substringAfter(':').trim() }.toList()
      val subnets = fields.firstOrNull { it.first == "Subnets" }?.second.orEmpty().split(' ').filter { it.isNotEmpty() }
      return fields.mapNotNull { (key, value) ->
        when (key) {
          "Node", "Reachability", "RTT" -> null
          "Address" -> endpoint(value)?.let { "Endpoint" to it }
          "Online since", "Last seen" -> key to if (value == "never") "Never" else value.substringBeforeLast(':')
          "PMTU" -> "Path MTU" to "$value bytes"
          "RX" -> "Received" to traffic(value)
          "TX" -> "Sent" to traffic(value)
          "Edges" -> "Links" to value.split(' ').filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "None" }
          "Subnets" -> if (subnets.size > 1) "Subnets" to subnets.joinToString(", ") else null
          "Status" -> "Flags" to value.split(' ').filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "None" }
          "Options" -> "Options" to value.split(' ').filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "None" }
          else -> if (value.isEmpty()) null else key to value
        }
      }.sortedBy { (label, _) -> DetailOrder.indexOf(label).let { if (it < 0) DetailOrder.size else it } }
    }

    // Connection facts first, protocol internals last.
    private val DetailOrder = listOf("Endpoint", "Online since", "Last seen", "Path MTU", "Received", "Sent", "Links", "Subnets",
      "Protocol", "Node ID", "Flags", "Options")

    private fun endpoint(value: String): String? {
      val host = value.substringBefore(" port ")
      val port = value.substringAfter(" port ", "")
      if (host == "unknown" || host.isEmpty()) return null
      val shown = if (':' in host) "[$host]" else host
      return if (port.isEmpty() || port == "unknown") host else "$shown:$port"
    }

    private fun traffic(value: String): String {
      val numbers = value.split(' ').mapNotNull { it.toLongOrNull() }
      if (numbers.size < 2) return value
      val (packets, bytes) = numbers
      return "${bytes(bytes)} · $packets packet${if (packets == 1L) "" else "s"}"
    }

    private fun bytes(n: Long): String {
      if (n < 1024) return "$n B"
      val units = listOf("KB", "MB", "GB", "TB")
      var value = n / 1024.0
      var unit = 0
      while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
      return "%.1f %s".format(java.util.Locale.ROOT, value, units[unit])
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
