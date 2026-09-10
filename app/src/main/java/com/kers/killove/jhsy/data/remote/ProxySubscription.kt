package com.kers.killove.jhsy.data.remote

import android.util.Base64
import com.kers.killove.jhsy.domain.ProxyNode
import com.kers.killove.jhsy.domain.ProxyType
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 订阅链接解析：支持
 * - 纯文本：每行一个节点（socks5://、http://、host:port、host:port:user:pass）
 * - Base64 编码的多行节点列表
 * - 简单 Clash YAML 片段中 type: http / socks5 的 proxies
 * - JSON 数组 [{name,type,server,port,username,password}]
 *
 * 不解析 vmess/ss/trojan 等需内核的协议（本应用仅 HTTP/SOCKS5）。
 */
object ProxySubscription {

    data class ImportResult(
        val nodes: List<ProxyNode>,
        val message: String
    )

    fun fetchAndParse(url: String): ImportResult {
        val u = url.trim()
        if (u.isEmpty()) return ImportResult(emptyList(), "订阅地址为空")
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
            // 当作本地粘贴的正文解析
            return parseBody(u)
        }
        return try {
            val req = Request.Builder().url(u).header("User-Agent", "jhsy-proxy/1.0").get().build()
            // 订阅拉取尽量直连，避免鸡生蛋
            ProxyHttp.executeDirect(req).use { resp ->
                if (!resp.isSuccessful) {
                    return ImportResult(emptyList(), "订阅下载失败 HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return ImportResult(emptyList(), "订阅内容为空")
                parseBody(body)
            }
        } catch (e: Exception) {
            ImportResult(emptyList(), "订阅下载失败: ${e.message}")
        }
    }

    fun parseBody(raw: String): ImportResult {
        val text = raw.trim()
        if (text.isEmpty()) return ImportResult(emptyList(), "内容为空")

        // JSON 数组
        if (text.startsWith("[")) {
            val fromJson = parseJsonArray(text)
            if (fromJson.isNotEmpty()) {
                return ImportResult(fromJson, "已解析 JSON ${fromJson.size} 个节点")
            }
        }

        // Clash-ish YAML proxies
        if (text.contains("proxies:") || text.contains("type: socks5") || text.contains("type: http")) {
            val fromYaml = parseClashLike(text)
            if (fromYaml.isNotEmpty()) {
                return ImportResult(fromYaml, "已解析 Clash 风格 ${fromYaml.size} 个节点")
            }
        }

        // 尝试 Base64（整段）
        val decoded = tryBase64(text)
        val linesSource = if (decoded != null && decoded.lines().any { looksLikeNodeLine(it) }) {
            decoded
        } else {
            text
        }

        val nodes = linesSource.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .mapNotNull { parseLine(it) }
            .distinctBy { "${it.type.code}|${it.host}|${it.port}|${it.user}" }
            .toList()

        return if (nodes.isEmpty()) {
            ImportResult(emptyList(), "未识别到 HTTP/SOCKS5 节点（不支持 ss/vmess/trojan）")
        } else {
            ImportResult(nodes, "已导入 ${nodes.size} 个节点")
        }
    }

    private fun tryBase64(s: String): String? {
        val cleaned = s.replace("\\s".toRegex(), "")
        if (cleaned.length < 16) return null
        return try {
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            try {
                val bytes = Base64.decode(cleaned, Base64.URL_SAFE or Base64.NO_WRAP)
                String(bytes, StandardCharsets.UTF_8).takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun looksLikeNodeLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("socks5://", true) ||
            t.startsWith("socks://", true) ||
            t.startsWith("http://", true) ||
            t.startsWith("https://", true) ||
            Regex("""^[\w.\-\[\]]+:\d{2,5}([:/].*)?$""").matches(t)
    }

    fun parseLine(line: String): ProxyNode? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        // scheme://
        if (raw.contains("://")) {
            return parseUri(raw)
        }
        // host:port or host:port:user:pass
        val parts = raw.split(":")
        if (parts.size >= 2) {
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            val user = parts.getOrNull(2)?.trim().orEmpty()
            val pass = parts.drop(3).joinToString(":").trim()
            return ProxyNode(
                id = newId(host, port),
                name = "$host:$port",
                type = ProxyType.Http,
                host = host,
                port = port,
                user = user,
                password = pass
            )
        }
        return null
    }

    private fun parseUri(raw: String): ProxyNode? {
        return try {
            var s = raw
            var name = ""
            val hash = s.indexOf('#')
            if (hash >= 0) {
                name = URLDecoder.decode(s.substring(hash + 1), "UTF-8")
                s = s.substring(0, hash)
            }
            val uri = URI(s)
            val scheme = (uri.scheme ?: "").lowercase()
            val type = when (scheme) {
                "socks5", "socks" -> ProxyType.Socks5
                "http", "https" -> ProxyType.Http
                else -> return null
            }
            val host = uri.host ?: return null
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "https" -> 443
                else -> 80
            }
            var user = uri.userInfo?.substringBefore(':')?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
            var pass = uri.userInfo?.substringAfter(':', "")?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
            // 查询参数兜底
            uri.query?.split('&')?.forEach { kv ->
                val k = kv.substringBefore('=')
                val v = URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
                when (k.lowercase()) {
                    "user", "username" -> user = v
                    "pass", "password" -> pass = v
                }
            }
            ProxyNode(
                id = newId(host, port, type),
                name = name.ifBlank { "$host:$port" },
                type = type,
                host = host,
                port = port,
                user = user,
                password = pass
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonArray(text: String): List<ProxyNode> {
        return try {
            val arr = JSONArray(text)
            val out = mutableListOf<ProxyNode>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = ProxyType.fromCode(o.optString("type", "http"))
                val host = o.optString("server").ifBlank { o.optString("host") }.ifBlank { o.optString("addr") }
                val port = o.optInt("port", 0)
                if (host.isBlank() || port !in 1..65535) continue
                val name = o.optString("name").ifBlank { "$host:$port" }
                out += ProxyNode(
                    id = o.optString("id").ifBlank { newId(host, port, type) },
                    name = name,
                    type = type,
                    host = host,
                    port = port,
                    user = o.optString("username").ifBlank { o.optString("user") },
                    password = o.optString("password").ifBlank { o.optString("pass") }
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 极简 YAML：- { name: x, type: socks5, server: h, port: 1080, ... } 或缩进行 */
    private fun parseClashLike(text: String): List<ProxyNode> {
        val out = mutableListOf<ProxyNode>()
        // 花括号单行
        Regex("""\{[^{}]+\}""").findAll(text).forEach { m ->
            val block = m.value
            val typeRaw = Regex("""type\s*:\s*(\w+)""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
            val type = when (typeRaw?.lowercase()) {
                "socks5", "socks" -> ProxyType.Socks5
                "http", "https" -> ProxyType.Http
                else -> return@forEach
            }
            val host = Regex("""(?:server|host)\s*:\s*([^\s,}\"]+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim('"', '\'') ?: return@forEach
            val port = Regex("""port\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val name = Regex("""name\s*:\s*[\"']?([^,\"'}]+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim() ?: "$host:$port"
            val user = Regex("""(?:username|user)\s*:\s*[\"']?([^,\"'}]*)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val pass = Regex("""(?:password|pass)\s*:\s*[\"']?([^,\"'}]*)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (port in 1..65535) {
                out += ProxyNode(newId(host, port, type), name, type, host, port, user, pass)
            }
        }
        return out.distinctBy { "${it.type.code}|${it.host}|${it.port}|${it.user}" }
    }

    private fun newId(host: String, port: Int, type: ProxyType = ProxyType.Http): String =
        "${type.code}_${host}_${port}_${UUID.randomUUID().toString().take(6)}"

    fun nodesToJson(list: List<ProxyNode>): String {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("name", n.name)
                    .put("type", n.type.code)
                    .put("host", n.host)
                    .put("port", n.port)
                    .put("user", n.user)
                    .put("password", n.password)
                    .put("latencyMs", n.latencyMs)
            )
        }
        return arr.toString()
    }

    fun nodesFromJson(json: String): List<ProxyNode> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<ProxyNode>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val host = o.optString("host")
                val port = o.optInt("port", 0)
                if (host.isBlank() || port !in 1..65535) continue
                out += ProxyNode(
                    id = o.optString("id").ifBlank { newId(host, port) },
                    name = o.optString("name").ifBlank { "$host:$port" },
                    type = ProxyType.fromCode(o.optString("type", "http")),
                    host = host,
                    port = port,
                    user = o.optString("user"),
                    password = o.optString("password"),
                    latencyMs = o.optLong("latencyMs", -1L)
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
