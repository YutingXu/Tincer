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
