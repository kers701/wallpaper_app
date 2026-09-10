package com.kers.killove.jhsy.data.remote

import android.content.Context
import com.kers.killove.jhsy.domain.ProxyType
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicReference

/**
 * 加速模式内置 / 可扩展节点。
 *
 * **安全说明**：
 * - 不内置来路不明的公共免费代理（极易被劫持、窃听、投毒）。
 * - 发布者应仅填入**自己可控**的中转（自建 VPS HTTP/SOCKS、Cloudflare Worker 出口等）。
 * - 也可在 assets 放置 `accel_nodes.json`，或设置远程列表 URL（由应用拉取）。
 *
 * JSON 格式示例：
 * ```json
 * [
 *   {"name":"hk-1","type":"http","host":"203.0.113.10","port":8080},
 *   {"name":"sg-1","type":"socks5","host":"203.0.113.20","port":1080,"user":"","password":""}
 * ]
 * ```
 */
object BuiltinAccelNodes {

    data class Node(
        val name: String,
        val type: ProxyType,
        val host: String,
        val port: Int,
        val user: String = "",
        val password: String = ""
    ) {
        val usable: Boolean get() = host.isNotBlank() && port in 1..65535

        fun toProxyConfig(): ProxyHttp.Config = ProxyHttp.Config(
            enabled = true,
            type = type,
            host = host,
            port = port,
            user = user,
            password = password
        )
    }

    /**
     * 编译期内置节点：请替换为你自己的中转。
     * 留空则仅依赖 assets / 远程列表。
     */
    private val compiled: List<Node> = listOf(
        Node("accel-1", ProxyType.Socks5, "172.245.228.92", 701),
        Node("accel-2", ProxyType.Socks5, "23.238.28.251", 701),
    )

    private val runtimeExtra = AtomicReference<List<Node>>(emptyList())

    fun setRemoteNodes(nodes: List<Node>) {
        runtimeExtra.set(nodes.filter { it.usable })
    }

    fun all(context: Context? = null): List<Node> {
        val fromAssets = context?.let { loadAssets(it) }.orEmpty()
        return (compiled + fromAssets + runtimeExtra.get())
            .filter { it.usable }
            .distinctBy { "${it.type.code}:${it.host}:${it.port}" }
    }

    fun randomOrNull(context: Context? = null): Node? {
        val list = all(context)
        if (list.isEmpty()) return null
        return list.random()
    }

    fun parseJson(text: String): List<Node> {
        val arr = JSONArray(text.trim())
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val host = o.optString("host").trim()
                val port = o.optInt("port", 0)
                if (host.isEmpty() || port !in 1..65535) continue
                val type = when (o.optString("type", "http").lowercase()) {
                    "socks5", "socks", "s5" -> ProxyType.Socks5
                    else -> ProxyType.Http
                }
                add(
                    Node(
                        name = o.optString("name", host),
                        type = type,
                        host = host,
                        port = port,
                        user = o.optString("user"),
                        password = o.optString("password")
                    )
                )
            }
        }
    }

    private fun loadAssets(context: Context): List<Node> = try {
        context.assets.open("accel_nodes.json").bufferedReader().use { parseJson(it.readText()) }
    } catch (_: Exception) {
        emptyList()
    }
}
