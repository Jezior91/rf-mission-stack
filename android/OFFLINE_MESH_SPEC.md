# RF Mission Stack — Android Offline Mesh (WiFi Direct P2P)

## Cel

Komunikacja Android↔Android **bez internetu, bez Wi-Fi AP, bez serwera**.  
Killer feature vs. FreeTAKServer (wymaga serwera) i Meshtastic (wymaga sprzętu LoRa).

---

## Architektura

```
[Android A]  ←── WiFi Direct ──→  [Android B]
     ↓                                  ↓
 libp2p UDP                         libp2p UDP
 (port 9000)                        (port 9000)
     ↓                                  ↓
[Mission State]  ←── CRDT sync ──→  [Mission State]
```

### Warstwy

| Warstwa | Technologia | Opis |
|---------|------------|------|
| **Transport** | Android WiFi Direct (WifiP2pManager) | Połączenie bez AP |
| **Discovery** | mDNS / WiFi Direct service discovery | Automatyczne wykrywanie |
| **P2P** | libp2p-android (QUIC/UDP) | Identyczny protokół co desktop |
| **Sync** | CRDT (Conflict-free Replicated Data Type) | Mergowanie stanu bez serwera |
| **Security** | Noise Protocol (libp2p wbudowany) | E2E encryption |

---

## Implementacja Kotlin

### 1. WiFiDirectManager.kt

```kotlin
package com.rfmission.app.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WifiDirectManager(private val context: Context) {

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, context.mainLooper, null)

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo

    fun discoverPeers() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { /* Discovery started */ }
            override fun onFailure(reason: Int) { /* Handle error */ }
        })
    }

    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { /* Connected */ }
            override fun onFailure(reason: Int) { /* Handle error */ }
        })
    }

    fun requestConnectionInfo(callback: (WifiP2pInfo) -> Unit) {
        manager.requestConnectionInfo(channel) { info ->
            _connectionInfo.value = info
            callback(info)
        }
    }

    // BroadcastReceiver do odbierania zdarzeń WiFi Direct
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager.requestPeers(channel) { peerList ->
                        _peers.value = peerList.deviceList.toList()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    requestConnectionInfo {}
                }
            }
        }
    }
}
```

### 2. OfflineMeshService.kt

```kotlin
package com.rfmission.app.network

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.net.ServerSocket
import java.net.Socket
import org.json.JSONObject

class OfflineMeshService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val MESH_PORT = 9001

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { startTcpServer() }
        return START_STICKY
    }

    private suspend fun startTcpServer() = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket(MESH_PORT)
        while (isActive) {
            val client = serverSocket.accept()
            launch { handleClient(client) }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val reader = socket.getInputStream().bufferedReader()
        val line = reader.readLine() ?: return@withContext
        try {
            val msg = JSONObject(line)
            when (msg.getString("type")) {
                "node_update" -> handleNodeUpdate(msg)
                "mission_sync" -> handleMissionSync(msg)
                "ping"        -> sendPong(socket)
            }
        } catch (e: Exception) { /* ignore malformed */ }
        socket.close()
    }

    private fun handleNodeUpdate(msg: JSONObject) {
        // Zapisz do lokalnej bazy (Room DB)
        // Broadcast przez LocalBroadcastManager do UI
    }

    private fun handleMissionSync(msg: JSONObject) {
        // CRDT merge misji
        // Zapisz wynik
    }

    private fun sendPong(socket: Socket) {
        val pong = JSONObject(mapOf("type" to "pong", "ts" to System.currentTimeMillis()))
        socket.getOutputStream().write((pong.toString() + "\n").toByteArray())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

### 3. MeshViewModel.kt (fragment)

```kotlin
// W MainViewModel.kt — dodaj obsługę mesh
val meshPeers = wifiDirectManager.peers
val isOfflineMode = MutableStateFlow(false)

fun sendToMeshPeer(peerIp: String, message: Map<String, Any>) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val socket = Socket(peerIp, 9001)
            val json = JSONObject(message).toString() + "\n"
            socket.getOutputStream().write(json.toByteArray())
            socket.close()
        } catch (e: Exception) {
            // Fallback: kolejkuj i wyślij gdy dostępny
        }
    }
}
```

---

## Protokół synchronizacji CRDT

### Mission State CRDT (Last-Writer-Wins Register)

```json
{
  "type": "mission_sync",
  "vector_clock": {"node_A": 5, "node_B": 3},
  "missions": [
    {
      "id": "m_001",
      "name": "Operacja Tarcza",
      "status": "active",
      "priority": 10,
      "last_modified_by": "node_A",
      "last_modified_ts": 1700000050,
      "nodes": ["node_A", "node_B"]
    }
  ]
}
```

**Reguła merge:** wygrywa `last_modified_ts` (LWW-Register).  
Dla konfliktów tej samej sekundy: wygrywa wyższy `priority`.

---

## Scenariusze użycia

| Scenariusz | Opis |
|-----------|------|
| **Teren bez zasięgu** | Dwa telefony łączą się przez WiFi Direct, synchronizują misje |
| **Hierarchia terenu** | Koordynator → Wykonawcy w topologii gwiazdy (1 GO + N clients) |
| **Powrót do sieci** | Po przywróceniu internetu: mesh state → Redis (auto-sync przez API) |
| **Multi-hop** | A↔B↔C — przez relay node (wymaga Bluetooth + WiFi Direct jednocześnie) |

---

## Harmonogram implementacji

| Etap | Funkcja | Czas |
|------|---------|------|
| **v7.1** | WiFi Direct discovery + połączenie 1:1 | 2 tygodnie |
| **v7.2** | Sync misji (CRDT LWW) | 2 tygodnie |
| **v7.3** | Sync węzłów + pozycja GPS | 1 tydzień |
| **v7.4** | Multi-hop relay | 3 tygodnie |
| **v7.5** | Auto-sync po powrocie do sieci | 1 tydzień |

---

## Przewaga nad konkurencją

| | RF Mission Stack Offline | Meshtastic | FreeTAKServer |
|--|--|--|--|
| **Sprzęt** | Tylko telefon | Wymaga LoRa dongle | Wymaga serwera |
| **Zasięg** | ~100m WiFi Direct | 10km LoRa | Internet |
| **Prędkość** | Wysoka (WiFi) | Niska (LoRa 250 bps) | Wysoka (Internet) |
| **Setup** | Zero | Zakup sprzętu | Instalacja serwera |
| **Rolowanie** | ETAP 8 ról | Brak ról | Podstawowe |
