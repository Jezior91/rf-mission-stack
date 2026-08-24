package com.rfmission.app.ui

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfmission.app.mesh.*

private val BG      = Color(0xFF0D1117)
private val SURFACE = Color(0xFF161B22)
private val ACCENT  = Color(0xFF00FF41)
private val WARN    = Color(0xFFFFAA00)
private val RED     = Color(0xFFFF4444)

/**
 * RF Mission Stack — Ekran Offline Mesh
 * WiFi Direct P2P bez internetu / AP.
 */
@Composable
fun MeshScreen(vm: MeshViewModel = viewModel()) {
    val wdState   by vm.wdState.collectAsState()
    val wdPeers   by vm.peers.collectAsState()
    val connInfo  by vm.connInfo.collectAsState()
    val meshPeers by vm.meshPeers.collectAsState()
    val meshLog   by vm.meshLog.collectAsState()
    val meshActive by vm.isMeshActive.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .padding(12.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────
        Text("📡 OFFLINE MESH", color = ACCENT, fontSize = 18.sp,
             fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("WiFi Direct P2P | ${wdState.name}", color = ACCENT.copy(alpha = 0.6f),
             fontSize = 11.sp, fontFamily = FontFamily.Monospace)

        Spacer(Modifier.height(8.dp))

        // ── Status bar ─────────────────────────────────────────────────
        StatusBar(wdState, connInfo, meshActive)

        Spacer(Modifier.height(8.dp))

        // ── Konfiguracja węzła ─────────────────────────────────────────
        NodeConfigCard(vm)

        Spacer(Modifier.height(8.dp))

        // ── Przyciski akcji ────────────────────────────────────────────
        ActionRow(
            wdState = wdState,
            meshActive = meshActive,
            onDiscover  = { vm.discoverPeers() },
            onStopDisc  = { vm.stopDiscovery() },
            onStopMesh  = { vm.stopMesh() },
            onStartSolo = { vm.startMeshService(isMaster = true) }
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Lewa kolumna: WiFi Direct peers
            Column(Modifier.weight(1f)) {
                SectionTitle("WYKRYTE URZĄDZENIA (${wdPeers.size})")
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(wdPeers) { dev ->
                        WdPeerRow(dev) { vm.connectToPeer(dev) }
                    }
                    if (wdPeers.isEmpty()) {
                        item { EmptyRow("Brak — uruchom wykrywanie") }
                    }
                }
            }
            // Prawa kolumna: Mesh nodes
            Column(Modifier.weight(1f)) {
                SectionTitle("WĘZŁY MESH (${meshPeers.size})")
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(meshPeers) { peer ->
                        MeshPeerRow(peer)
                    }
                    if (meshPeers.isEmpty()) {
                        item { EmptyRow("Brak węzłów mesh") }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Log konsoli ────────────────────────────────────────────────
        SectionTitle("LOG MESH")
        val listState = rememberLazyListState()
        LaunchedEffect(meshLog.size) {
            if (meshLog.isNotEmpty()) listState.animateScrollToItem(meshLog.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SURFACE, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            items(meshLog) { line ->
                Text(line, color = ACCENT.copy(alpha = 0.8f), fontSize = 10.sp,
                     fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
            }
            if (meshLog.isEmpty()) {
                item { EmptyRow("Brak logów") }
            }
        }
    }
}

@Composable
private fun StatusBar(state: WifiDirectState, connInfo: android.net.wifi.p2p.WifiP2pInfo?, meshActive: Boolean) {
    val color = when (state) {
        WifiDirectState.CONNECTED   -> ACCENT
        WifiDirectState.CONNECTING,
        WifiDirectState.DISCOVERING -> WARN
        WifiDirectState.DISABLED    -> RED
        else                        -> Color.Gray
    }
    Row(Modifier
        .fillMaxWidth()
        .background(SURFACE, RoundedCornerShape(6.dp))
        .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(state.name, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        if (connInfo != null) {
            val role = if (connInfo.isGroupOwner) "MASTER" else "LEAF"
            Text(role, color = ACCENT, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                 fontWeight = FontWeight.Bold)
        }
        if (meshActive) {
            Text("MESH ON", color = ACCENT, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun NodeConfigCard(vm: MeshViewModel) {
    var callsign by remember { mutableStateOf(vm.callsign) }
    var role     by remember { mutableStateOf(vm.role) }
    val roles    = listOf("MET","KOR","WYK","INF","PRB","PMR","KMB","OBS")
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier
        .fillMaxWidth()
        .background(SURFACE, RoundedCornerShape(6.dp))
        .padding(8.dp)
    ) {
        Text("KONFIGURACJA WĘZŁA", color = ACCENT.copy(0.6f), fontSize = 10.sp,
             fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it; vm.callsign = it },
                label = { Text("Callsign", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ACCENT, unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = role,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rola ETAP", fontSize = 10.sp) },
                    modifier = Modifier.clickable { expanded = true }.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ACCENT, unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = ACCENT, unfocusedTextColor = ACCENT
                    )
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                             modifier = Modifier.background(SURFACE)) {
                    roles.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r, color = ACCENT, fontFamily = FontFamily.Monospace) },
                            onClick = { role = r; vm.role = r; expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    wdState: WifiDirectState, meshActive: Boolean,
    onDiscover: () -> Unit, onStopDisc: () -> Unit,
    onStopMesh: () -> Unit, onStartSolo: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!meshActive) {
            MeshButton("🔍 Szukaj",    ACCENT, Modifier.weight(1f)) { onDiscover() }
            MeshButton("⬛ Stop",      Color.Gray, Modifier.weight(1f)) { onStopDisc() }
            MeshButton("🟢 Solo",      WARN, Modifier.weight(1f)) { onStartSolo() }
        } else {
            MeshButton("🔴 Stop Mesh", RED, Modifier.weight(1f)) { onStopMesh() }
        }
    }
}

@Composable
private fun MeshButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier,
           colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
           shape = RoundedCornerShape(6.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun WdPeerRow(dev: WifiP2pDevice, onClick: () -> Unit) {
    val status = when (dev.status) {
        WifiP2pDevice.CONNECTED     -> "✅"
        WifiP2pDevice.INVITED       -> "⏳"
        WifiP2pDevice.FAILED        -> "❌"
        WifiP2pDevice.AVAILABLE     -> "📶"
        else -> "?"
    }
    Row(Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(dev.deviceName.ifBlank { dev.deviceAddress }, color = Color.White,
                 fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(dev.deviceAddress, color = Color.Gray, fontSize = 9.sp,
                 fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MeshPeerRow(peer: com.rfmission.app.mesh.PeerEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("📡", fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Column {
            Row {
                Text(peer.callsign, color = Color.White, fontSize = 11.sp,
                     fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("[${peer.role}]", color = ACCENT, fontSize = 10.sp,
                     fontFamily = FontFamily.Monospace)
            }
            Text(peer.addr.hostAddress ?: "-", color = Color.Gray, fontSize = 9.sp,
                 fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = ACCENT.copy(0.5f), fontSize = 10.sp,
         fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun EmptyRow(t: String) {
    Text(t, color = Color.DarkGray, fontSize = 10.sp,
         fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
}
