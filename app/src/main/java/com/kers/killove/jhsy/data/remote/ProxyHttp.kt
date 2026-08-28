package com.kers.killove.jhsy.data.remote

import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.ProxyType
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 全局 HTTP：支持可选 HTTP / SOCKS5 代理；代理不可用时自动回退系统直连。
 * 多进程各自维护内存客户端，通过 [applySettings] 同步配置。
 *
 * SOCKS5 用户名密码：依赖 JVM 对 SOCKS 的有限支持；无认证节点最稳妥。
 */
object ProxyHttp {

    data class Config(
        val enabled: Boolean = false,
        val type: ProxyType = ProxyType.Http,
        val host: String = "",
        val port: Int = 0,
        val user: String = "",
        val password: String = ""
    ) {
        val usable: Boolean
            get() = enabled && host.isNotBlank() && port in 1..65535

        companion object {
            fun from(s: AppSettings) = Config(
                enabled = s.proxyEnabled,
                type = s.proxyType,
                host = s.proxyHost.trim(),
                port = s.proxyPort,
                user = s.proxyUser,
                password = s.proxyPassword
            )
        }
    }

    private val configRef = AtomicReference(Config())
    private val directRef = AtomicReference(buildDirect())
    private val proxyRef = AtomicReference<OkHttpClient?>(null)

    fun applySettings(settings: AppSettings) {
        applyConfig(Config.from(settings))
    }

    fun applyConfig(cfg: Config) {
        val old = configRef.get()
        if (old == cfg) return
        configRef.set(cfg)
        proxyRef.set(if (cfg.usable) buildProxy(cfg) else null)
    }

    fun currentConfig(): Config = configRef.get()

    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)

    private fun buildDirect(): OkHttpClient = baseBuilder().build()

    private fun buildProxy(cfg: Config): OkHttpClient {
        val proxyType = when (cfg.type) {
            ProxyType.Socks5 -> Proxy.Type.SOCKS
            ProxyType.Http -> Proxy.Type.HTTP
        }
        if (cfg.type == ProxyType.Socks5 && (cfg.user.isNotBlank() || cfg.password.isNotBlank())) {
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                    if (requestingHost.equals(cfg.host, true) || requestingPort == cfg.port) {
                        return java.net.PasswordAuthentication(
                            cfg.user,
                            cfg.password.toCharArray()
                        )
                    }
                    return null
                }
            })
        }
        val proxy = Proxy(proxyType, InetSocketAddress(cfg.host, cfg.port))
        val b = baseBuilder().proxy(proxy)
        if (cfg.type == ProxyType.Http && (cfg.user.isNotBlank() || cfg.password.isNotBlank())) {
            val cred = Credentials.basic(cfg.user, cfg.password)
            b.proxyAuthenticator(Authenticator { _: Route?, response: Response ->
                if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
                response.request.newBuilder()
                    .header("Proxy-Authorization", cred)
                    .build()
            })
        }
        return b.build()
    }

    fun clientFor(cfg: Config): OkHttpClient {
        if (!cfg.usable) return directRef.get()
        return buildProxy(cfg)
    }

    fun execute(request: Request): Response {
        val cfg = configRef.get()
        val proxyClient = proxyRef.get()
        if (cfg.usable && proxyClient != null) {
            try {
                return proxyClient.newCall(request).execute()
            } catch (_: IOException) {
            } catch (_: Exception) {
            }
        }
        return directRef.get().newCall(request).execute()
    }

    fun executeDirect(request: Request): Response =
        directRef.get().newCall(request).execute()

    fun measureLatencyMs(
        cfg: Config,
        url: String = "https://wallhaven.cc/",
        timeoutSec: Long = 12
    ): Long {
        if (!cfg.usable) return -1L
        return try {
            val client = clientFor(cfg).newBuilder()
                .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                .readTimeout(timeoutSec, TimeUnit.SECONDS)
                .callTimeout(timeoutSec + 2, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "jhsy-proxy-probe/1.0")
                .build()
            val t0 = System.nanoTime()
            client.newCall(req).execute().use { resp ->
                val ms = (System.nanoTime() - t0) / 1_000_000L
                if (resp.code in 100..599) ms else -1L
            }
        } catch (_: Exception) {
            try {
                val client = clientFor(cfg).newBuilder()
                    .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                    .readTimeout(timeoutSec, TimeUnit.SECONDS)
                    .callTimeout(timeoutSec + 2, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "jhsy-proxy-probe/1.0")
                    .header("Range", "bytes=0-0")
                    .build()
                val t0 = System.nanoTime()
                client.newCall(req).execute().use {
                    (System.nanoTime() - t0) / 1_000_000L
                }
            } catch (_: Exception) {
                -1L
            }
        }
    }
}
