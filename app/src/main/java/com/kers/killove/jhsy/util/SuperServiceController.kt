package com.kers.killove.jhsy.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.kers.killove.jhsy.service.KeepAliveAccessibilityService
import com.kers.killove.jhsy.service.WallpaperForegroundService

data class SuperServiceStatus(
    val hasRoot: Boolean,
    val hasAccessibility: Boolean,
    val canEnable: Boolean,
    val activeRoot: Boolean,
    val message: String
)

object SuperServiceController {

    fun status(context: Context): SuperServiceStatus {
        val root = RootKeepAlive.hasRoot()
        val a11y = isAccessibilityEnabled(context)
        val can = root || a11y
        val msg = when {
            root && a11y -> "Root + 无障碍均可用，可开启超级服务"
            root -> "已检测到 Root，可开启超级服务"
            a11y -> "无障碍已开启，可开启超级服务"
            else -> "需要 Root 或无障碍权限才能开启超级服务"
        }
        return SuperServiceStatus(root, a11y, can, root, msg)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = ComponentName(context, KeepAliveAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) } ||
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { si ->
                    si.packageName == context.packageName &&
                        si.name == KeepAliveAccessibilityService::class.java.name
                } }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            BatteryHelper.openAppBatterySettings(context)
        }
    }

    /**
     * @return null 表示成功；否则为失败原因
     */
    fun enable(context: Context): String? {
        val st = status(context)
        if (!st.canEnable) {
            openAccessibilitySettings(context)
            return "请先授予无障碍权限，或提供 Root"
        }
        WallpaperForegroundService.start(context)
        if (st.hasRoot) {
            val ok = RootKeepAlive.startDaemon(context)
            if (!ok && !st.hasAccessibility) {
                return "Root 守护启动失败"
            }
        }
        return null
    }

    fun disable(context: Context) {
        RootKeepAlive.stopDaemon()
        // 无障碍由用户在系统设置中关闭；此处只停服务循环由开关控制
    }
}
