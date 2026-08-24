package com.rfmission.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfmission.app.data.*
import com.rfmission.app.network.ApiService
import com.rfmission.app.network.WsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val prefs = UserPreferences(app)
    private val apiService = ApiService()
    private val wsService = WsService()

    val nodes: StateFlow<List<NodeEntity>> = db.nodeDao().getAllNodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missions: StateFlow<List<MissionEntity>> = db.missionDao().getAllMissions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isConnected: StateFlow<Boolean> = wsService.isConnected
    val preferences: StateFlow<Map<String, String>> = prefs.allPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken

    init {
        viewModelScope.launch {
            preferences.collect { p ->
                val url = p["ws_url"] ?: return@collect
                wsService.connect(url, _authToken.value)
            }
        }
        viewModelScope.launch {
            wsService.messages.collect { msg -> handleWsMessage(msg) }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val serverUrl = preferences.value["server_url"] ?: return@launch
            try {
                val resp = apiService.login(serverUrl, username, password)
                _authToken.value = resp.access_token
                refresh()
            } catch (e: Exception) { /* log */ }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val serverUrl = preferences.value["server_url"] ?: return@launch
            val token = _authToken.value ?: return@launch
            try {
                val remoteNodes = apiService.getNodes(serverUrl, token)
                db.nodeDao().upsertAll(remoteNodes.map { n ->
                    NodeEntity(id=n.id, name=n.name, status=n.status, role=n.role, ip=n.ip, lastSeen=n.last_seen ?: 0)
                })
                val remoteMissions = apiService.getMissions(serverUrl, token)
                db.missionDao().upsertAll(remoteMissions.map { m ->
                    MissionEntity(id=m.id, name=m.name, status=m.status, priority=m.priority)
                })
            } catch (e: Exception) { /* work offline */ }
        }
    }

    private fun handleWsMessage(msg: String) {
        viewModelScope.launch {
            try {
                // Parse and update local DB from WS events
                refresh()
            } catch (_: Exception) {}
        }
    }

    fun savePreferences(map: Map<String, String>) {
        viewModelScope.launch { prefs.saveAll(map) }
    }

    override fun onCleared() {
        super.onCleared()
        wsService.disconnect()
    }
}
