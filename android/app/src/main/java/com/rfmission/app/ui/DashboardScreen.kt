package com.rfmission.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfmission.app.viewmodel.MainViewModel
import com.rfmission.app.data.NodeEntity
import com.rfmission.app.data.MissionEntity

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val nodes by viewModel.nodes.collectAsState()
    val missions by viewModel.missions.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Connection status bar
        ConnectionStatusBar(isConnected)
        Spacer(modifier = Modifier.height(16.dp))

        // Stats row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Węzły", nodes.size.toString(), Icons.Default.Sensors,
                if (nodes.any { it.status == "online" }) MaterialTheme.colorScheme.primary else Color.Gray)
            StatCard("Online", nodes.count { it.status == "online" }.toString(),
                Icons.Default.CheckCircle, Color(0xFF4CAF50))
            StatCard("Misje", missions.size.toString(), Icons.Default.Assignment,
                MaterialTheme.colorScheme.secondary)
            StatCard("Aktywne", missions.count { it.status == "active" }.toString(),
                Icons.Default.PlayArrow, Color(0xFFFF9800))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Ostatnie zdarzenia", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (nodes.isEmpty() && missions.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null,
                        tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Brak danych — połącz się z siecią", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nodes.take(5)) { node -> NodeEventCard(node) }
                items(missions.take(3)) { mission -> MissionEventCard(mission) }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(connected: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (connected) Color(0xFF1B5E20) else Color(0xFFB71C1C)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (connected) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null, tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (connected) "Połączono z serwerem RF Mission" else "Rozłączono — tryb offline",
                color = Color.White, fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Card(modifier = Modifier.width(80.dp)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun NodeEventCard(node: NodeEntity) {
    val statusColor = when (node.status) {
        "online" -> Color(0xFF4CAF50)
        "degraded" -> Color(0xFFFF9800)
        else -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Router, contentDescription = null, tint = statusColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, fontWeight = FontWeight.Medium)
                Text(node.id, fontSize = 11.sp, color = Color.Gray)
            }
            Chip(label = node.status, color = statusColor)
        }
    }
}

@Composable
fun MissionEventCard(mission: MissionEntity) {
    val statusColor = when (mission.status) {
        "active" -> Color(0xFF2196F3)
        "completed" -> Color(0xFF4CAF50)
        "failed" -> Color(0xFFF44336)
        else -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = statusColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mission.name, fontWeight = FontWeight.Medium)
                Text("Priorytet: ${mission.priority}", fontSize = 11.sp, color = Color.Gray)
            }
            Chip(label = mission.status, color = statusColor)
        }
    }
}

@Composable
fun Chip(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
