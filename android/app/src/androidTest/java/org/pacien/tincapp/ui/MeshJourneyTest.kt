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
    compose.onNodeWithText("Create network").performClick()
    compose.onNodeWithText("Network name").performTextInput("home")
    compose.onNodeWithText("Create").performClick()
    compose.activityRule.scenario.recreate()
    waitFor("Disconnected")
    assertTrue(AppPaths.defaultEd25519PrivateKeyFile("home").exists())
    val publicConfiguration = Tinc.export("home").get(10, TimeUnit.SECONDS)
    assertTrue(publicConfiguration.contains("Ed25519PublicKey"))
    assertFalse(publicConfiguration.contains("PRIVATE KEY"))

    compose.onNodeWithText("android_phone", substring = true).assertExists()
    compose.onNodeWithText("No peers").assertExists()
    compose.onNodeWithContentDescription("Settings").performClick()
    compose.onNodeWithText("Dark").performScrollTo().performClick()
    compose.activityRule.scenario.recreate()
    waitFor("Appearance")
    assertEquals("Dark", compose.activity.getSharedPreferences("mesh_ui", 0).getString("appearance", null))
    compose.onNode(hasText("Dark") and isSelected(), useUnmergedTree = false).assertExists()

    compose.onNodeWithText("DNS").performScrollTo().performClick()
    compose.onNodeWithText("DNS servers").performTextInput("192.168.1.1")
    compose.onAllNodesWithText("Excluded apps").onLast().performScrollTo().performClick()
    compose.onNodeWithText("Search apps").performTextInput("Settings")
    val settingsRow = hasText("Settings") and hasClickAction() and !hasSetTextAction()
    // Package enumeration runs off the UI thread, including after recreation.
    compose.waitUntil(10_000) { compose.onAllNodes(settingsRow).fetchSemanticsNodes().isNotEmpty() }
    compose.onNode(settingsRow).performClick()
    val settingsSwitch = isToggleable() and hasAnySibling(hasText("Settings"))
    compose.onNode(settingsSwitch, useUnmergedTree = true).assertIsOn()
    compose.activityRule.scenario.recreate()
    compose.onNode(hasSetTextAction()).assertTextEquals("Settings")
    compose.waitUntil(10_000) { compose.onAllNodes(settingsSwitch, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    compose.onNode(settingsSwitch, useUnmergedTree = true).assertIsOn()
    compose.onNodeWithText("Done").performClick()
    compose.onNodeWithText("Save").performClick()
    compose.activityRule.scenario.recreate()
    compose.waitUntil(10_000) { org.pacien.tincapp.data.VpnInterfaceConfiguration.fromIfaceConfiguration(AppPaths.netConfFile("home")).dnsServers == listOf("192.168.1.1") }
    assertEquals(listOf("com.android.settings"), org.pacien.tincapp.data.VpnInterfaceConfiguration.fromIfaceConfiguration(AppPaths.netConfFile("home")).disallowedApplications)
    compose.waitUntil(10_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
    waitFor("Appearance")
    compose.onNodeWithContentDescription("Back").performClick()
    waitFor("Disconnected")

    compose.onNodeWithContentDescription("Connect").performClick()
    val deny = device.wait(Until.findObject(By.res("android:id/button2")), 5000)
    assertNotNull("Android VPN permission should be requested", deny)
    deny.click()
    waitFor("Tincer needs VPN permission to connect.")
    compose.onNodeWithText("OK").performClick()
    compose.onNodeWithText("Disconnected").assertExists()

    compose.onNodeWithContentDescription("Connect").performClick()
    val allow = device.wait(Until.findObject(By.res("android:id/button1")), 5000)
    assertNotNull("VPN permission should be requested again", allow)
    allow.click()
    val declineNotifications = device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_deny_button"))), 5000)
    assertNotNull("Notification permission is optional and requested after VPN permission", declineNotifications)
    declineNotifications.click()
    waitFor("Waiting for peers")
    compose.onNodeWithText("Connected").assertDoesNotExist()
    assertTrue(TincVpnService.isConnected())

    compose.activityRule.scenario.recreate()
    waitFor("Waiting for peers")
    compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    waitFor("Waiting for peers")
    assertTrue(TincVpnService.isConnected())
    compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    device.pressHome()
    device.waitForIdle()
    assertTrue(TincVpnService.isConnected())
    device.executeShellCommand("am start -n dev.yuting.tinc/org.pacien.tincapp.ui.MainActivity")
    waitFor("Waiting for peers")
    compose.onNodeWithContentDescription("Disconnect").performClick()
    waitFor("Disconnected")
    assertFalse(TincVpnService.isConnected())
  }
}
