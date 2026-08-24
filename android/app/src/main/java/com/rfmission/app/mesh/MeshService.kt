package com.rfmission.app.mesh

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * RF Mission Stack — MeshService (Foreground Service)
 * Obsługuje UDP socket + wątek odbioru + heartbeat PING/PONG.
 * Role: MASTER (Group Owner) otwiera server socket; RELAY/LEAF łączą się do MASTER.
 */
class MeshService : Service() {

    companion object {
        const val MESH_PORT         = 9000
        const val PING_INTERVAL_MS  = 5_000L
        const val TIMEOUT_MS        = 15_000L
        const val NOTIF_CHANNEL_ID  = "rf_mesh"
        const val NOTIF_ID          = 42
        private val json            = Json { ignoreUnknownKeys = true }
    }

    private val scope    = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket   : DatagramSocket? = null
    private val seqGen   = AtomicInteger(0)
    private val peers    = mutableMapOf<String, PeerEntry>()

    private val _log     = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _peerList = MutableStateFlow<List<PeerEntry>>(emptyList())
    val peerList: StateFlow<List<PeerEntry>> = _peerList

    var localNodeId  = "node-${System.currentTimeMillis()}"
    var localRole    = "OBS"
    var localCallsign = "UNKNOWN"
    var masterAddr   : InetAddress? = null
    var isMaster     = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
        startForeground(NOTIF_ID, buildNotif("Mesh aktywny"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            localNodeId   = it.getStringExtra("nodeId")   ?: localNodeId
            localRole     = it.getStringExtra("role")     ?: localRole
            localCallsign = it.getStringExtra("callsign") ?: localCallsign
            isMaster      = it.getBooleanExtra("isMaster", false)
            val masterIp  = it.getStringExtra("masterIp")
            masterAddr    = masterIp?.let { ip -> InetAddress.getByName(ip) }
        }
        startMesh()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        socket?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Mesh core ─────────────────────────────────────────────────────────────
    private fun startMesh() {
        socket = DatagramSocket(MESH_PORT).also { s -> s.broadcast = true }
        log("Mesh start | master=$isMaster | port=$MESH_PORT")

        // Receiver loop
        scope.launch {
            val buf = ByteArray(MeshPacket.MAX_UDP)
            while (isActive) {
                try {
                    val dp = DatagramPacket(buf, buf.size)
                    socket?.receive(dp)
                    val pkt = MeshPacket.decode(dp.data.copyOf(dp.length)) ?: continue
                    handlePacket(pkt, dp.address)
                } catch (_: Exception) {}
            }
        }

        // Heartbeat
        scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                sendNodeInfo()
                evictTimedOut()
            }
        }

        // Announce self
        scope.launch { delay(500); sendNodeInfo() }
    }

    private fun handlePacket(pkt: MeshPacket, from: InetAddress) {
        when (pkt.type) {
            PacketType.PING -> {
                send(MeshPacket(PacketType.PONG, seqGen.incrementAndGet(), localNodeId), from)
            }
            PacketType.PONG -> {
                peers[pkt.senderId]?.lastSeen = System.currentTimeMillis()
            }
            PacketType.NODE_INFO -> {
                val info = runCatching { json.decodeFromString<NodeInfoPayload>(pkt.payload) }.getOrNull()
                    ?: return
                peers[pkt.senderId] = PeerEntry(pkt.senderId, info.role, info.callsign, from, System.currentTimeMillis())
                _peerList.value = peers.values.toList()
                log("Peer: ${info.callsign} [${info.role}] @ ${from.hostAddress}")
                // relay: jeśli jesteśmy masterem, forward do pozostałych
                if (isMaster) relay(pkt, excludeAddr = from)
            }
            PacketType.MISSION_SYNC, PacketType.CRDT_DELTA, PacketType.COT_EVENT -> {
                log("${pkt.type.name} od ${pkt.senderId}")
                if (isMaster) relay(pkt, excludeAddr = from)
            }
            else -> {}
        }
    }

    private fun sendNodeInfo() {
        val payload = json.encodeToString(NodeInfoPayload(
            nodeId = localNodeId, role = localRole, callsign = localCallsign
        ))
        val pkt = MeshPacket(PacketType.NODE_INFO, seqGen.incrementAndGet(), localNodeId, payload)
        masterAddr?.let { send(pkt, it) }
            ?: broadcastSend(pkt)
    }

    private fun relay(pkt: MeshPacket, excludeAddr: InetAddress) {
        peers.values.filter { it.addr != excludeAddr }.forEach { send(pkt, it.addr) }
    }

    private fun send(pkt: MeshPacket, addr: InetAddress) {
        runCatching {
            val data = MeshPacket.encode(pkt)
            socket?.send(DatagramPacket(data, data.size, addr, MESH_PORT))
        }
    }

    private fun broadcastSend(pkt: MeshPacket) {
        runCatching {
            val data = MeshPacket.encode(pkt)
            val ba = InetAddress.getByName("255.255.255.255")
            socket?.send(DatagramPacket(data, data.size, ba, MESH_PORT))
        }
    }

    private fun evictTimedOut() {
        val now = System.currentTimeMillis()
        peers.entries.removeIf { now - it.value.lastSeen > TIMEOUT_MS }
        _peerList.value = peers.values.toList()
    }

    private fun log(msg: String) {
        _log.value = (_log.value + "[${ts()}] $msg").takeLast(200)
    }

    private fun ts(): String {
        val f = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return f.format(java.util.Date())
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotifChannel() {
        val ch = NotificationChannel(NOTIF_CHANNEL_ID, "RF Mesh", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(text: String) = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setContentTitle("RF Mission Mesh")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .build()
}

data class PeerEntry(
    val nodeId   : String,
    val role     : String,
    val callsign : String,
    val addr     : InetAddress,
    var lastSeen : Long
)
