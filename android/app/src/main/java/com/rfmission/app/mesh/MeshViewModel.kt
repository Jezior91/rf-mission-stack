package com.rfmission.app.mesh

import android.app.Application
import android.content.*
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * RF Mission Stack — MeshViewModel
 * Spina WifiDirectManager + MeshService + UI.
 */
class MeshViewModel(app: Application) : AndroidViewModel(app) {

    val wifiDirect = WifiDirectManager(app)

    // Stan WiFi Direct
    val wdState  = wifiDirect.state
    val peers    = wifiDirect.peers
    val connInfo = wifiDirect.connInfo

    // Stan usługi Mesh
    private var meshService: MeshService? = null

    private val _meshPeers = MutableStateFlow<List<PeerEntry>>(emptyList())
    val meshPeers: StateFlow<List<PeerEntry>> = _meshPeers.asStateFlow()

    private val _meshLog = MutableStateFlow<List<String>>(emptyList())
    val meshLog: StateFlow<List<String>> = _meshLog.asStateFlow()

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    // Konfiguracja węzła
    var nodeId   = "node-android-${System.currentTimeMillis()}"
    var role     = "OBS"
    var callsign = "ANDROID-1"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            // MeshService nie udostępnia Bindera w tej wersji
        }
        override fun onServiceDisconnected(name: ComponentName) {
            meshService = null
            _isMeshActive.value = false
        }
    }

    fun discoverPeers() = wifiDirect.discoverPeers()

    fun connectToPeer(device: WifiP2pDevice) {
        wifiDirect.connect(device) { success ->
            if (success) {
                viewModelScope.launch {
                    connInfo.filterNotNull().first().let { info ->
                        startMeshService(
                            isMaster  = info.isGroupOwner,
                            masterIp  = if (!info.isGroupOwner) info.groupOwnerAddress.hostAddress else null
                        )
                    }
                }
            }
        }
    }

    fun startMeshService(isMaster: Boolean, masterIp: String? = null) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MeshService::class.java).apply {
            putExtra("nodeId",    nodeId)
            putExtra("role",      role)
            putExtra("callsign",  callsign)
            putExtra("isMaster",  isMaster)
            masterIp?.let { putExtra("masterIp", it) }
        }
        ctx.startForegroundService(intent)
        _isMeshActive.value = true
    }

    fun stopMesh() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, MeshService::class.java))
        wifiDirect.disconnect()
        _isMeshActive.value = false
    }

    fun stopDiscovery() = wifiDirect.stopDiscovery()
}
