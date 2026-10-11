// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.pacien.tincapp.intent.Actions

class MainActivity : ComponentActivity() {
  private val model: MeshViewModel by viewModels()

  private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) model.connect(model.pendingPassphrase)
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
        onScan = {
          scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan a tinc invitation").setBeepEnabled(false).setOrientationLocked(false))
        })
    }
    if (savedInstanceState == null && intent.action == Actions.ACTION_CONNECT)
      intent.data?.schemeSpecificPart?.let { model.select(it); connect(intent.data?.fragment) }
  }

  private fun connect(passphrase: String?) {
    val request = VpnService.prepare(this)
    if (request == null) model.connect(passphrase)
    else {
      model.pendingPassphrase = passphrase
      permission.launch(request)
    }
  }

  private fun share(text: String) {
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
      .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share public peer configuration"))
  }
}
