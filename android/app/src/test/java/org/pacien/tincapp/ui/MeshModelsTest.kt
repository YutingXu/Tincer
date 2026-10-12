package org.pacien.tincapp.ui

import org.junit.Assert.*
import org.junit.Test
import org.pacien.tincapp.data.CidrAddress
import org.pacien.tincapp.data.VpnInterfaceConfiguration
import java.nio.file.Files

class MeshModelsTest {
  @Test fun transportAndLatencyComeFromTheDaemon() {
    val peer = MeshPeer.fromInfo("peer_a", """
      Node:         peer_a
      Address:      192.0.2.1 port 655
      Reachability: directly with TCP
      RTT:          7.269
      Subnets:      10.77.0.2
    """.trimIndent())
    assertEquals("Direct TCP", peer.transport)
    assertTrue(peer.reachable)
    assertEquals("7.3 ms", peer.latency)
    assertEquals("10.77.0.2", peer.address)
    assertEquals("Via peer_b", MeshPeer.fromInfo("peer_a", "Reachability: none, forwarded via peer_b").transport)
    assertEquals("Via peer_b", MeshPeer.fromInfo("peer_a", "Reachability: indirectly via peer_b").transport)
    assertFalse(MeshPeer.fromInfo("peer_a", "Reachability: unreachable").reachable)
    assertFalse(MeshPeer.fromInfo("peer_a", "Reachability: unknown").reachable)
    assertFalse(MeshPeer("peer_a").reachable)
  }

  @Test fun daemonInfoBecomesLabelledDetails() {
    val details = MeshPeer.details("""
      Node:         peer_a
      Node ID:      5b0a2c1f9e3d
      Address:      192.0.2.1 port 655
      Online since: 2026-10-11 11:40:34
      Status:       validkey visited reachable sptps udp_confirmed
      Options:      pmtu_discovery clamp_mss
      Protocol:     17.7
      Reachability: directly with UDP
      PMTU:         1518
      RTT:          7.269
      RX:           1 packets  512 bytes
      TX:           2048 packets  3145728 bytes
      Edges:        peer_b peer_c
      Subnets:      10.77.0.2
    """.trimIndent()).toMap()
    assertEquals("192.0.2.1:655", details["Endpoint"])
    assertEquals("2026-10-11 11:40", details["Online since"])
    assertEquals("validkey, visited, reachable, sptps, udp_confirmed", details["Flags"])
    assertEquals("1518 bytes", details["Path MTU"])
    assertEquals("512 B · 1 packet", details["Received"])
    assertEquals("3.0 MB · 2048 packets", details["Sent"])
    assertEquals("peer_b, peer_c", details["Links"])
    assertEquals("5b0a2c1f9e3d", details["Node ID"])
    // Already shown elsewhere in the sheet.
    assertFalse(listOf("Node", "Reachability", "RTT", "Subnets").any { it in details })

    val offline = MeshPeer.details("Address:      unknown port unknown\nLast seen:    never\nOptions:     \nSubnets:      10.77.0.4 fd00::4").toMap()
    assertNull(offline["Endpoint"])
    assertEquals("Never", offline["Last seen"])
    assertEquals("None", offline["Options"])
    assertEquals("10.77.0.4, fd00::4", offline["Subnets"])
    assertEquals("[fd00::1]:655", MeshPeer.details("Address: fd00::1 port 655").toMap()["Endpoint"])
  }

  @Test fun savingRoutesPreservesDnsAndAppBypassWithoutAccumulatingOldValues() {
    val file = Files.createTempFile("tinc-network", ".conf").toFile()
    try {
      val original = VpnInterfaceConfiguration(
        addresses = listOf(CidrAddress("10.77.0.3", 32)), routes = listOf(CidrAddress("10.77.0.0", 24)),
        dnsServers = listOf("192.168.1.1"), disallowedApplications = listOf("com.android.chrome"), mtu = 1400)
      original.write(file)
      original.copy(routes = listOf(CidrAddress("10.77.0.2", 32))).write(file)
      val saved = VpnInterfaceConfiguration.fromIfaceConfiguration(file)
      assertEquals(listOf(CidrAddress("10.77.0.2", 32)), saved.routes)
      assertEquals(original.addresses, saved.addresses)
      assertEquals(original.dnsServers, saved.dnsServers)
      assertEquals(original.disallowedApplications, saved.disallowedApplications)
      assertEquals(1400, saved.mtu)
    } finally { file.delete() }
  }
}
