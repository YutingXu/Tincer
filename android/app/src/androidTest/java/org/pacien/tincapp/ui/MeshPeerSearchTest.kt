package org.pacien.tincapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.pacien.tincapp.data.VpnInterfaceConfiguration

class MeshPeerSearchTest {
  @get:Rule val compose = createComposeRule()

  @Test fun switchingNetworksClearsPeerSearch() {
    val profiles = listOf("large", "small").map {
      MeshProfile(it, "phone", VpnInterfaceConfiguration(), emptyList())
    }
    val state = mutableStateOf(MeshState(profiles = profiles, selectedName = "large",
      peers = (1..7).map { MeshPeer("peer$it") }))
    compose.setContent {
      MeshTheme(Appearance.Light) {
        HomeScreen(state.value, busy = false, onConnect = {},
          onNetworks = { state.value = state.value.copy(selectedName = "small", peers = listOf(MeshPeer("tablet"))) },
          onSettings = {}, onJoin = {}, onCreate = {}, onAddPeer = {}, onPeer = {}, onCopy = {})
      }
    }

    compose.onNodeWithText("Search peers").performScrollTo().performTextInput("peer1")
    compose.onNode(hasText("peer1") and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Switch network").performClick()
    compose.onNodeWithText("tablet").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Search peers").assertDoesNotExist()
    compose.onNodeWithText("No peers match “peer1”.").assertDoesNotExist()
  }

  @Test fun disconnectingKeepsAnActiveSearchAvailableToClear() {
    val profile = MeshProfile("mesh", "phone", VpnInterfaceConfiguration(), listOf("peer1"))
    val state = mutableStateOf(MeshState(profiles = listOf(profile), selectedName = profile.name,
      phase = ConnectionPhase.Active, peers = (1..7).map { MeshPeer("peer$it", reachable = true) }))
    compose.setContent {
      MeshTheme(Appearance.Light) {
        HomeScreen(state.value, busy = false,
          onConnect = { state.value = state.value.copy(phase = ConnectionPhase.Offline, peers = profile.peers.map { MeshPeer(it) }) },
          onNetworks = {}, onSettings = {}, onJoin = {}, onCreate = {}, onAddPeer = {}, onPeer = {}, onCopy = {})
      }
    }

    compose.onNodeWithText("Search peers").performScrollTo().performTextInput("peer7")
    compose.onNode(hasText("peer7") and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
    compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
    compose.onNodeWithContentDescription("Disconnect").performClick()
    compose.onNode(hasSetTextAction()).performScrollTo().assertTextEquals("peer7").performTextClearance()
    compose.onNodeWithText("peer1").performScrollTo().assertIsDisplayed()
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
    compose.onNodeWithText("No peers match “peer7”.").assertDoesNotExist()
  }
}
