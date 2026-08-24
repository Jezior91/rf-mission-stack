package com.rfmission.app.mesh

import kotlinx.serialization.Serializable

/**
 * RF Mission Stack — Protokół pakietów Mesh v1
 * Format UDP: [MAGIC:2][TYPE:1][SEQ:4][SENDER_LEN:1][SENDER:n][PAYLOAD_LEN:2][PAYLOAD:m]
 */
@Serializable
data class MeshPacket(
    val type     : PacketType,
    val seq      : Int,
    val senderId : String,
    val payload  : String = ""
) {
    companion object {
        private val MAGIC = byteArrayOf(0x52, 0x46) // "RF"
        const val MAX_UDP = 1400

        fun encode(pkt: MeshPacket): ByteArray {
            val sBytes = pkt.senderId.toByteArray(Charsets.UTF_8)
            val pBytes = pkt.payload.toByteArray(Charsets.UTF_8)
            val buf = java.io.ByteArrayOutputStream()
            buf.write(MAGIC)
            buf.write(pkt.type.code.toInt())
            buf.write(pkt.seq shr 24 and 0xFF); buf.write(pkt.seq shr 16 and 0xFF)
            buf.write(pkt.seq shr  8 and 0xFF); buf.write(pkt.seq        and 0xFF)
            buf.write(sBytes.size)
            buf.write(sBytes)
            buf.write(pBytes.size shr 8 and 0xFF); buf.write(pBytes.size and 0xFF)
            buf.write(pBytes)
            return buf.toByteArray()
        }

        fun decode(data: ByteArray): MeshPacket? = runCatching {
            if (data.size < 10 || data[0] != MAGIC[0] || data[1] != MAGIC[1]) return null
            val type   = PacketType.fromCode(data[2])
            val seq    = (data[3].toInt() and 0xFF shl 24) or (data[4].toInt() and 0xFF shl 16) or
                         (data[5].toInt() and 0xFF shl 8)  or (data[6].toInt() and 0xFF)
            val sLen   = data[7].toInt() and 0xFF
            val sender = String(data, 8, sLen, Charsets.UTF_8)
            val pOff   = 8 + sLen
            val pLen   = (data[pOff].toInt() and 0xFF shl 8) or (data[pOff + 1].toInt() and 0xFF)
            MeshPacket(type, seq, sender, String(data, pOff + 2, pLen, Charsets.UTF_8))
        }.getOrNull()
    }
}

enum class PacketType(val code: Byte) {
    PING(0x01), PONG(0x02),
    NODE_INFO(0x10),
    MISSION_SYNC(0x20), CRDT_DELTA(0x21),
    COT_EVENT(0x30),
    ACK(0xF0.toByte()), ERR(0xFF.toByte());
    companion object { fun fromCode(b: Byte) = entries.firstOrNull { it.code == b } ?: ERR }
}

@Serializable data class NodeInfoPayload(
    val nodeId: String, val role: String, val callsign: String,
    val lat: Double = 0.0, val lon: Double = 0.0, val alt: Double = 0.0,
    val ts: Long = System.currentTimeMillis()
)
@Serializable data class MissionSyncPayload(
    val missionId: String, val title: String, val status: String,
    val assignedRole: String, val ts: Long = System.currentTimeMillis()
)
