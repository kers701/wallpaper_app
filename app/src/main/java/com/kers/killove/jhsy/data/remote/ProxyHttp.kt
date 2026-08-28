package com.kers.killove.jhsy.data.remote

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.ProxyType
import com.kers.killove.jhsy.util.SuperProxyController
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
 * 全局 HTTP 代理链路（仅本应用）：
 * 1. 超级代理可用（已启用 + 普通代理已开 + 内核在跑）→ 127.0.0.1 SOCKS5
 * 2. 否则普通代理可用 → HTTP/SOCKS5 节点
 * 3. 否则系统直连
 *
 * 请求失败时按上述顺序自动降级，不抛出「代理挂了整条链路死」的单点故障。
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
            /** 普通代理配置（不含超级代理）。 */
            fun normalFrom(s: AppSettings): Config = Config(
                enabled = s.proxyEnabled,
                type = s.proxyType,
                host = s.proxyHost.trim(),
                port = s.proxyPort,
                user = s.proxyUser,
                password = s.proxyPassword
            )

            /** 超级代理本地 SOCKS（需内核在跑才真正可用）。 */
            fun superFrom(s: AppSettings): Config {
                val port = s.superProxyLocalPort.coerceIn(1025, 65535)
                return Config(
                    enabled = s.proxyEnabled && s.superProxyEnabled,
                    type = ProxyType.Socks5,
                    host = "127.0.0.1",
                    port = port,
                    user = "",
                    password = ""
                )
            }

            /** 兼容旧调用：优先超级端口展示，否则普通代理。 */
            fun from(s: AppSettings, superRunning: Boolean = false): Config {
                val superCfg = superFrom(s)
                if (superRunning && superCfg.usable) return superCfg
                return normalFrom(s)
            }
        }
    }

    private val settingsRef = AtomicReference(AppSettings())
    private val superRunningRef = AtomicReference(false)
    private val superClientRef = AtomicReference<OkHttpClient?>(null)
    private val proxyClientRef = AtomicReference<OkHttpClient?>(null)
    private val directRef = AtomicReference(buildDirect())

    fun applySettings(settings: AppSettings) {
        applySettings(null, settings)
    }

    fun applySettings(context: Context?, settings: AppSettings) {
        settingsRef.set(settings)
        val superRunning = if (context != null && settings.proxyEnabled && settings.superProxyEnabled) {
            SuperProxyController.status(context, settings).running
        } else {
            superRunningRef.get() && settings.proxyEnabled && settings.superProxyEnabled
        }
        superRunningRef.set(superRunning)

        val superCfg = Config.superFrom(settings)
        val normalCfg = Config.normalFrom(settings)

        superClientRef.set(
            if (superRunning && superCfg.usable) buildProxy(superCfg) else null
        )
        proxyClientRef.set(
            if (normalCfg.usable) buildProxy(normalCfg) else null
        )
    }

    /** 启动/停止超级内核后调用，刷新是否走本地端口。 */
    fun setSuperRunning(running: Boolean) {
        superRunningRef.set(running)
        val s = settingsRef.get()
        applySettings(null, s.copy()) // rebuild clients using updated flag
        // re-apply with explicit running
        val superCfg = Config.superFrom(s)
        val normalCfg = Config.normalFrom(s)
        val useSuper = running && s.proxyEnabled && s.superProxyEnabled && superCfg.usable
        superRunningRef.set(useSuper)
        superClientRef.set(if (useSuper) buildProxy(superCfg) else null)
        proxyClientRef.set(if (normalCfg.usable) buildProxy(normalCfg) else null)
    }

    fun applyConfig(cfg: Config) {
        // 兼容旧路径：当作普通代理
        proxyClientRef.set(if (cfg.usable) buildProxy(cfg) else null)
    }

    fun currentConfig(): Config {
        if (superClientRef.get() != null) {
            return Config.superFrom(settingsRef.get())
        }
        return Config.normalFrom(settingsRef.get())
    }

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

    /**
     * 请求执行：超级代理 → 普通代理 → 系统网络。
     */
    fun execute(request: Request): Response {
        val superClient = superClientRef.get()
        if (superClient != null) {
            try {
                return superClient.newCall(request).execute()
            } catch (_: IOException) {
            } catch (_: Exception) {
            }
        }
        val proxyClient = proxyClientRef.get()
        if (proxyClient != null) {
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
