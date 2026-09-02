package com.zcode.remote

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Connection(
    val id: String,
    val name: String,
    val url: String,
    val createdAt: Long
)

/** 连接保存在 SharedPreferences（JSON 数组），纯本地、无需任何权限。 */
class ConnectionsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("connections", Context.MODE_PRIVATE)

    fun list(): List<Connection> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Connection(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("url"),
                    o.getLong("createdAt")
                )
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Connection? = list().firstOrNull { it.id == id }

    fun add(name: String, url: String): Connection {
        val conn = Connection(UUID.randomUUID().toString(), name, url, System.currentTimeMillis())
        save(list() + conn)
        return conn
    }

    fun remove(id: String) {
        save(list().filter { it.id != id })
    }

    fun defaultNameFor(url: String, index: Int): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        return if (host.isNullOrBlank()) "连接 $index" else host
    }

    private fun save(items: List<Connection>) {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("url", c.url)
                put("createdAt", c.createdAt)
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private companion object {
        const val KEY_LIST = "list"
    }
}