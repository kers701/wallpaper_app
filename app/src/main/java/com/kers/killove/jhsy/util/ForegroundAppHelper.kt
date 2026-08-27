package com.kers.killove.jhsy.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings

data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

object ForegroundAppHelper {

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    /** 当前前台应用包名；无权限或失败返回 null */
    fun currentForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 60_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)
                ?: return null
            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    fun appLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai)?.toString() ?: packageName
        } catch (_: Exception) {
            packageName
        }
    }

    fun isBlacklistedForeground(context: Context, blacklist: List<String>): Boolean {
        if (blacklist.isEmpty()) return false
        val fg = currentForegroundPackage(context) ?: return false
        val set = blacklist.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return fg in set
    }

    /** 可启动的应用列表（用户点图标能打开的） */
    fun listLaunchableApps(context: Context): List<LauncherAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val self = context.packageName
        val map = linkedMapOf<String, LauncherAppInfo>()
        for (ri in resolves) {
            val pkg = ri.activityInfo.packageName
            if (pkg == self || pkg.endsWith(".debug") && self.startsWith(pkg.removeSuffix(".debug"))) {
                // still allow listing self? skip self
                if (pkg == self || pkg == self.removeSuffix(".debug") || self.startsWith(pkg)) continue
            }
            if (pkg in map) continue
            val label = try {
                ri.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) {
                pkg
            }
            val isSystem = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) {
                false
            }
            map[pkg] = LauncherAppInfo(pkg, label, isSystem)
        }
        return map.values.sortedBy { it.label.lowercase() }
    }
}
