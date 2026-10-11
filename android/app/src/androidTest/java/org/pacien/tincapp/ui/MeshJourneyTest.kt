package org.pacien.tincapp.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.pacien.tincapp.commands.Tinc
import org.pacien.tincapp.context.AppPaths
import org.pacien.tincapp.service.TincVpnService
import java.util.concurrent.TimeUnit

class MeshJourneyTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()
  private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

  private fun waitFor(text: String) = compose.waitUntil(20_000) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  @Test fun createConfigureAndConnectWithoutRootAcrossActivityRecreation() {
    compose.onNodeWithText("Add your first network").performClick()
    compose.onNodeWithText("Create a network").performClick()
    compose.onNodeWithText("Network name").performTextInput("home")
    compose.onNodeWithText("Create network").performClick()
    compose.activityRule.scenario.recreate()
    waitFor("Ready to connect.")
    assertTrue(AppPaths.defaultEd25519PrivateKeyFile("home").exists())
    val publicConfiguration = Tinc.export("home").get(10, TimeUnit.SECONDS)
    assertTrue(publicConfiguration.contains("Ed25519PublicKey"))
    assertFalse(publicConfiguration.contains("PRIVATE KEY"))

    compose.onNode(hasText("Devices") and hasClickAction()).performClick()
    compose.onNodeWithText("android_phone").assertExists()
    compose.onNodeWithText("Your mesh starts here").assertExists()
    compose.onNode(hasText("Settings") and hasClickAction()).performClick()
    compose.onNodeWithText("Appearance").performClick()
    compose.onNodeWithText("Dark").performClick()
    compose.activityRule.scenario.recreate()
    waitFor("Network and app preferences.")
    compose.onNodeWithText("Dark").assertExists()

    compose.onNodeWithText("DNS").performClick()
    compose.onNodeWithText("DNS servers (optional)").performTextInput("192.168.1.1")
    compose.onNodeWithText("Save").performClick()
    compose.activityRule.scenario.recreate()
    compose.waitUntil(10_000) { org.pacien.tincapp.data.VpnInterfaceConfiguration.fromIfaceConfiguration(AppPaths.netConfFile("home")).dnsServers == listOf("192.168.1.1") }
    compose.waitUntil(10_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
    waitFor("Network and app preferences.")
    compose.onNodeWithText("Home").performClick()

    compose.onNodeWithText("Connect").performClick()
    val deny = device.wait(Until.findObject(By.res("android:id/button2")), 5000)
    assertNotNull("Android VPN permission should be requested", deny)
    deny.click()
    waitFor("VPN permission was declined. You can try connecting again.")
    compose.onNodeWithText("Got it").performClick()
    compose.onNodeWithText("Ready to connect.").assertExists()

    compose.onNodeWithText("Connect").performClick()
    val allow = device.wait(Until.findObject(By.res("android:id/button1")), 5000)
    assertNotNull("VPN permission should be requested again", allow)
    allow.click()
    waitFor("VPN is active.")
    compose.onNodeWithText("Waiting for a reachable peer.").assertExists()
    compose.onNodeWithText("You're connected.").assertDoesNotExist()
    assertTrue(TincVpnService.isConnected())

    compose.activityRule.scenario.recreate()
    waitFor("VPN is active.")
    compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    waitFor("VPN is active.")
    assertTrue(TincVpnService.isConnected())
    compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    device.pressHome()
    device.waitForIdle()
    assertTrue(TincVpnService.isConnected())
    device.executeShellCommand("am start -n dev.yuting.tinc/org.pacien.tincapp.ui.MainActivity")
    waitFor("VPN is active.")
    compose.onNodeWithText("Disconnect").performClick()
    waitFor("Ready to connect.")
    assertFalse(TincVpnService.isConnected())
  }
}
