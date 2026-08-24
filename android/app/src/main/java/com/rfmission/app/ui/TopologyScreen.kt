package com.rfmission.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfmission.app.data.NodeEntity
import com.rfmission.app.viewmodel.MainViewModel
import kotlin.math.*

@Composable
fun TopologyScreen(viewModel: MainViewModel) {
    val nodes by viewModel.nodes.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("Mapa węzłów") }, icon = { Icon(Icons.Default.Hub, null) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("Lista") }, icon = { Icon(Icons.Default.List, null) })
        }
        when (selectedTab) {
            0 -> TopologyMapView(nodes)
            1 -> NodeListView(nodes)
        }
    }
}

@Composable
fun TopologyMapView(nodes: List<NodeEntity>) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    if (nodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DeviceHub, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Brak węzłów w sieci", color = Color.Gray, fontSize = 16.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.3f, 4f)
                        offset += pan
                    }
                }
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = minOf(cx, cy) * 0.7f
            val positions = nodes.mapIndexed { i, node ->
                val angle = (2 * PI * i / nodes.size) - PI / 2
                val x = cx + radius * cos(angle).toFloat()
                val y = cy + radius * sin(angle).toFloat()
                node to Offset(x, y)
            }
            scale(scale, pivot = Offset(cx, cy)) {
                translate(offset.x, offset.y) {
                    // Draw edges
                    for (i in positions.indices) {
                        for (j in i + 1 until positions.size) {
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.3f),
                                start = positions[i].second,
                                end = positions[j].second,
                                strokeWidth = 1f
                            )
                        }
                    }
                    // Draw nodes
                    positions.forEach { (node, pos) ->
                        val color = when (node.status) {
                            "online" -> Color(0xFF4CAF50)
                            "degraded" -> Color(0xFFFF9800)
                            else -> Color(0xFF9E9E9E)
                        }
                        drawCircle(color = color.copy(alpha = 0.2f), radius = 30f, center = pos)
                        drawCircle(color = color, radius = 16f, center = pos)
                    }
                }
            }
        }
        // Legend
        Card(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                LegendItem("Online", Color(0xFF4CAF50))
                LegendItem("Degraded", Color(0xFFFF9800))
                LegendItem("Offline", Color(0xFF9E9E9E))
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Surface(color = color, modifier = Modifier.size(10.dp), shape = MaterialTheme.shapes.small) {}
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
fun NodeListView(nodes: List<NodeEntity>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(nodes) { node ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val color = when (node.status) {
                        "online" -> Color(0xFF4CAF50)
                        "degraded" -> Color(0xFFFF9800)
                        else -> Color.Gray
                    }
                    Icon(Icons.Default.Router, null, tint = color, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(node.name, fontWeight = FontWeight.Bold)
                        Text(node.id, fontSize = 11.sp, color = Color.Gray)
                        Text("Rola: ${node.role}", fontSize = 11.sp)
                        if (node.ip != null) Text("IP: ${node.ip}", fontSize = 11.sp, color = Color.Gray)
                    }
                    Chip(label = node.status, color = color)
                }
            }
        }
    }
}
