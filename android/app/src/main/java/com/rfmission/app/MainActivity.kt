package com.rfmission.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfmission.app.ui.*
import com.rfmission.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RFMissionApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RFMissionApp() {
    val viewModel: MainViewModel = viewModel()
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf(
        Triple("Dashboard", Icons.Default.Dashboard, 0),
        Triple("Topologia", Icons.Default.Hub, 1),
        Triple("Sieć", Icons.Default.Wifi, 2),
        Triple("Ustawienia", Icons.Default.Settings, 3)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RF Mission Stack 6.2") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel)
                1 -> TopologyScreen(viewModel)
                2 -> NetworkScreen(viewModel)
                3 -> SettingsScreen(viewModel)
            }
        }
    }
}
