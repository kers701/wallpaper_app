package com.kers.killove.jhsy.data.remote

import com.kers.killove.jhsy.domain.AppSettings
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
 * 全局 HTTP：支持可选代理；代理不可用时自动回退系统直连。
 * 多进程各自维护内存客户端，通过 [applySettings] 同步配置。
 */
object ProxyHttp {

    data class Config(
        val enabled: Boolean = false,
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
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(cfg.host, cfg.port))
        val b = baseBuilder().proxy(proxy)
        if (cfg.user.isNotBlank() || cfg.password.isNotBlank()) {
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

    /** 优先代理；连接失败或代理不可用则直连 */
    fun execute(request: Request): Response {
        val cfg = configRef.get()
        val proxyClient = proxyRef.get()
        if (cfg.usable && proxyClient != null) {
            try {
                return proxyClient.newCall(request).execute()
            } catch (_: IOException) {
                // 代理不可用 → 系统网络
            } catch (_: Exception) {
            }
        }
        return directRef.get().newCall(request).execute()
    }

    /** 仅直连（少数场景） */
    fun executeDirect(request: Request): Response =
        directRef.get().newCall(request).execute()
}
