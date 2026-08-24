package com.rfmission.app.network

import com.rfmission.app.data.NodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LoginResponse(val access_token: String, val role: String)
data class RemoteNode(val id: String, val name: String, val status: String, val role: String, val ip: String?, val last_seen: Long?)
data class RemoteMission(val id: String, val name: String, val status: String, val priority: Int)

class ApiService {
    suspend fun login(base: String, user: String, pass: String): LoginResponse = withContext(Dispatchers.IO) {
        val url = URL("$base/auth/login")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        OutputStreamWriter(conn.outputStream).use { it.write("""{"username":"$user","password":"$pass"}""") }
        val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        val j = JSONObject(body)
        LoginResponse(j.getString("access_token"), j.getString("role"))
    }

    suspend fun getNodes(base: String, token: String): List<RemoteNode> = withContext(Dispatchers.IO) {
        val url = URL("$base/nodes")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteNode(o.getString("id"), o.getString("name"), o.optString("status","offline"),
                o.optString("role","observer"), o.optString("ip",null), o.optLong("last_seen",0))
        }
    }

    suspend fun getMissions(base: String, token: String): List<RemoteMission> = withContext(Dispatchers.IO) {
        val url = URL("$base/missions")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteMission(o.getString("id"), o.getString("name"), o.optString("status","pending"), o.optInt("priority",0))
        }
    }
}
