package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 当前实际访问方式（加速 / 超级代理 / 网络代理 / 系统直连）。
 * 加速模式与用户代理互斥；超级代理需代理已开且内核在跑。
 */
object AccessMode {
    enum class Kind { Accel, Super, Proxy, Direct }

    fun kind(settings: AppSettings, superRunning: Boolean): Kind {
        if (settings.accelModeEnabled && settings.accelPrivacyAccepted) return Kind.Accel
        if (settings.proxyEnabled && settings.superProxyEnabled && superRunning) return Kind.Super
        val proxyUsable = settings.proxyEnabled &&
            settings.proxyHost.isNotBlank() &&
            settings.proxyPort in 1..65535
        if (proxyUsable) return Kind.Proxy
        return Kind.Direct
    }

    fun kind(context: Context, settings: AppSettings): Kind {
        val running = runCatching {
            SuperProxyController.status(context, settings).running
        }.getOrDefault(false)
        return kind(settings, running)
    }

    fun label(kind: Kind): String = when (kind) {
        Kind.Accel -> "加速模式"
        Kind.Super -> "超级代理"
        Kind.Proxy -> "网络代理"
        Kind.Direct -> "系统直连"
    }

    fun label(settings: AppSettings, superRunning: Boolean): String =
        label(kind(settings, superRunning))

    fun line(settings: AppSettings, superRunning: Boolean): String =
        "访问方式：${label(settings, superRunning)}"

    fun line(context: Context, settings: AppSettings): String =
        "访问方式：${label(kind(context, settings))}"
}
