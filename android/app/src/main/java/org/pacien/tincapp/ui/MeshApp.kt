// SPDX-License-Identifier: GPL-3.0-or-later
package org.pacien.tincapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pacien.tincapp.BuildConfig
import org.pacien.tincapp.utils.TincKeyring
import kotlin.coroutines.cancellation.CancellationException

internal enum class Page { Home, Settings, Peer }
internal enum class Sheet { None, Networks, Join, Create, AddPeer, NetworkSettings, Logs, Unlock }

@Composable
fun MeshApp(model: MeshViewModel, onConnect: (String?) -> Unit, onShare: (String) -> Unit, onScan: () -> Unit, onNotifications: () -> Unit) {
  val state by model.state.collectAsStateWithLifecycle()
  var page by model.page
  var sheet by model.sheet
  var peerName by model.peer
  val snackbar = remember { SnackbarHostState() }
  val context = LocalContext.current
  LifecycleStartEffect(Unit) { model.observe(true); onStopOrDispose { model.observe(false) } }
  LaunchedEffect(state.notice) {
    state.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() }
  }

  // Predictive back: the page shrinks toward the gesture and the home screen is revealed when it commits.
  var backProgress by remember { mutableFloatStateOf(0f) }
  PredictiveBackHandler(enabled = page != Page.Home) { events ->
    try {
      events.collect { backProgress = it.progress }
      page = Page.Home
    } catch (cancelled: CancellationException) {
      throw cancelled
    } finally {
      backProgress = 0f
    }
  }
  val back by animateFloatAsState(backProgress, Motion.effects(), label = "back")
  // Home is disposed while another page is shown; keep its scroll position and play its entrance only once.
  val homeList = rememberLazyListState()
  var homeShown by rememberSaveable { mutableStateOf(false) }

  val connect = {
    if (state.phase == ConnectionPhase.Active) model.disconnect()
    else if (state.profile != null && TincKeyring.needsPassphrase(state.profile!!.name)) sheet = Sheet.Unlock
    else onConnect(null)
  }
  val copy: (String) -> Unit = { text ->
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Tincer", text))
  }
  val shareKey = { model.exportPublic(onShare) }
  val close = { sheet = Sheet.None }

  MeshTheme(state.appearance) {
    // Screens handle insets themselves so lists can scroll under the navigation bar.
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
      SharedPages {
        AnimatedContent(page, Modifier.fillMaxSize().padding(padding), label = "page", transitionSpec = {
          when {
            // The peer page grows out of its row; only fade the rest.
            Page.Peer in listOf(initialState, targetState) -> fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects())
            // Material shared axis: forward slides in from the end, back from the start.
            else -> {
              val forward = targetState != Page.Home
              (slideInHorizontally(Motion.spatial()) { if (forward) it / 8 else -it / 8 } + fadeIn(Motion.effects())) togetherWith
                (slideOutHorizontally(Motion.spatial()) { if (forward) -it / 8 else it / 8 } + fadeOut(Motion.effects()))
            }
          }
        }) { current ->
          SharedPage {
            Box(Modifier.fillMaxSize().graphicsLayer {
              if (current != Page.Home && back > 0f) {
                scaleX = 1f - back * .1f; scaleY = scaleX
                translationX = back * 24.dp.toPx()
                shape = RoundedCornerShape(32.dp * back); clip = true
              }
            }, contentAlignment = Alignment.TopCenter) {
              when (current) {
                Page.Home -> CompositionLocalProvider(LocalAppear provides !homeShown) {
                  LaunchedEffect(Unit) { homeShown = true }
                  HomeScreen(state, state.busy && sheet == Sheet.None, connect, onNetworks = { sheet = Sheet.Networks },
                    onSettings = { page = Page.Settings }, onJoin = { sheet = Sheet.Join }, onCreate = { sheet = Sheet.Create },
                    onAddPeer = { sheet = Sheet.AddPeer }, onPeer = { peerName = it.name; page = Page.Peer }, onCopy = copy, listState = homeList)
                }
                Page.Settings -> SettingsScreen(state, BuildConfig.VERSION_NAME, onBack = { page = Page.Home },
                  onNetworkSettings = { sheet = Sheet.NetworkSettings }, onShareKey = shareKey, onAppearance = model::setAppearance,
                  onNotifications = onNotifications, onLog = { sheet = Sheet.Logs })
                Page.Peer -> peerName?.let { name ->
                  // Follow live status while the page is open.
                  PeerScreen(state.peers.firstOrNull { it.name == name } ?: MeshPeer(name), state.phase, onBack = { page = Page.Home }, onCopy = copy)
                }
              }
            }
          }
        }
      }
    }

    when (sheet) {
      Sheet.None -> Unit
      Sheet.Networks -> NetworksSheet(state, onSelect = { model.select(it); close() }, onJoin = { sheet = Sheet.Join },
        onCreate = { sheet = Sheet.Create }, onDismiss = close)
      Sheet.Create -> CreateNetworkSheet(state.busy, close) { name, node, address, routes ->
        model.create(name, node, address, routes) { close(); page = Page.Home }
      }
      Sheet.Join -> JoinNetworkSheet(state.busy, state.scannedInvitation, { model.scanned(null) }, onScan, close) { name, invitation ->
        model.join(name, invitation) { close(); page = Page.Home }
      }
      Sheet.AddPeer -> AddPeerSheet(state.busy, shareKey, close) { public, endpoint -> model.addPeer(public, endpoint) { close() } }
      Sheet.NetworkSettings -> state.profile?.let { profile ->
        NetworkSettingsSheet(profile, state.busy, { installedApps(context) }, close) { addresses, routes, dns, apps ->
          model.saveSettings(addresses, routes, dns, apps) { close() }
        }
      }
      Sheet.Logs -> {
        val log by produceState<String?>(null, state.selectedName) { value = model.log() }
        LogSheet(log, copy, close)
      }
      Sheet.Unlock -> UnlockDialog(close) { passphrase -> close(); onConnect(passphrase) }
    }
    state.error?.let { error -> ErrorDialog(error, model::dismissError) }
  }
}

private suspend fun installedApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
  val packages = context.packageManager
  val size = (36 * context.resources.displayMetrics.density).toInt()
  packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
    .filter { it.activityInfo.packageName != BuildConfig.APPLICATION_ID }
    .distinctBy { it.activityInfo.packageName }
    .map { info ->
      val icon = runCatching { info.activityInfo.applicationInfo.loadIcon(packages).toBitmap(size, size).asImageBitmap() }.getOrNull()
      InstalledApp(info.activityInfo.packageName, info.loadLabel(packages).toString(), icon)
    }
    .sortedBy { it.label.lowercase() }
}
