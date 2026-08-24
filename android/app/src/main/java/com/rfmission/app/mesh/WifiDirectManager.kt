package com.rfmission.app.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.net.wifi.p2p.WifiP2pManager.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/**
 * RF Mission Stack — WiFi Direct Manager
 * Transport P2P bez AP, bez internetu.
 */
@SuppressLint("MissingPermission")
class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: Channel = manager.initialize(context, context.mainLooper, null)

    private val _peers   = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connInfo: StateFlow<WifiP2pInfo?> = _connInfo.asStateFlow()

    private val _state = MutableStateFlow(WifiDirectState.IDLE)
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    // ── Broadcast Receiver ────────────────────────────────────────────────────
    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(EXTRA_WIFI_STATE, -1) == WIFI_P2P_STATE_ENABLED
                    _state.value = if (enabled) WifiDirectState.IDLE else WifiDirectState.DISABLED
                }
                WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager.requestPeers(channel) { peers ->
                        _peers.value = peers.deviceList.toList()
                    }
                }
                WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                        android.net.wifi.p2p.WifiP2pManager.EXTRA_NETWORK_INFO
                    )
                    if (networkInfo?.isConnected == true) {
                        manager.requestConnectionInfo(channel) { info ->
                            _connInfo.value = info
                            _state.value = WifiDirectState.CONNECTED
                        }
                    } else {
                        _connInfo.value = null
                        _state.value = WifiDirectState.IDLE
                    }
                }
            }
        }
    }

    val intentFilter = IntentFilter().apply {
        addAction(WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    // ── API ───────────────────────────────────────────────────────────────────
    fun discoverPeers(onResult: (Boolean) -> Unit = {}) {
        _state.value = WifiDirectState.DISCOVERING
        manager.discoverPeers(channel, actionListener(
            onSuccess = { onResult(true) },
            onFailure = { _state.value = WifiDirectState.IDLE; onResult(false) }
        ))
    }

    fun connect(device: WifiP2pDevice, onResult: (Boolean) -> Unit = {}) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup    = android.net.wifi.WpsInfo.PBC
            groupOwnerIntent = 15 // preferuj bycie GROUP OWNER (server)
        }
        _state.value = WifiDirectState.CONNECTING
        manager.connect(channel, config, actionListener(
            onSuccess = { onResult(true) },
            onFailure = { _state.value = WifiDirectState.IDLE; onResult(false) }
        ))
    }

    fun disconnect(onResult: (Boolean) -> Unit = {}) {
        manager.removeGroup(channel, actionListener(
            onSuccess = { _connInfo.value = null; _state.value = WifiDirectState.IDLE; onResult(true) },
            onFailure = { onResult(false) }
        ))
    }

    fun stopDiscovery() {
        manager.stopPeerDiscovery(channel, actionListener())
    }

    private fun actionListener(
        onSuccess: () -> Unit = {},
        onFailure: (Int) -> Unit = {}
    ) = object : ActionListener {
        override fun onSuccess()          = onSuccess()
        override fun onFailure(r: Int)    = onFailure(r)
    }
}

enum class WifiDirectState { DISABLED, IDLE, DISCOVERING, CONNECTING, CONNECTED }
