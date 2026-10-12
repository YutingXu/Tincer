// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.pacien.tincapp.intent.Actions
import org.pacien.tincapp.service.TincVpnService

class MainActivity : ComponentActivity() {
  private val model: MeshViewModel by viewModels()
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

  private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) startConnection(model.pendingPassphrase)
    else model.permissionDenied()
    model.pendingPassphrase = null
  }
  private val scanner = registerForActivityResult(ScanContract()) { result ->
    result.contents?.let { model.scanned(it) }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MeshApp(model, onConnect = ::connect, onShare = ::share,
        onNotifications = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) },
        onScan = {
          scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan an invitation").setBeepEnabled(false).setOrientationLocked(false))
        })
    }
    if (savedInstanceState == null && intent.action == Actions.ACTION_CONNECT)
      intent.data?.schemeSpecificPart?.let { model.select(it); connect(intent.data?.fragment) }
  }

  override fun onResume() {
    super.onResume()
    TincVpnService.refreshNotification()
  }

  private fun connect(passphrase: String?) {
    val request = VpnService.prepare(this)
    if (request == null) startConnection(passphrase)
    else {
      model.pendingPassphrase = passphrase
      permission.launch(request)
    }
  }

  private fun startConnection(passphrase: String?) {
    model.connect(passphrase)
    val permissions = getSharedPreferences("permissions", MODE_PRIVATE)
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
      !permissions.getBoolean("notificationsAsked", false)) {
      permissions.edit().putBoolean("notificationsAsked", true).apply()
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun share(text: String) {
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
      .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share public key"))
  }
}
