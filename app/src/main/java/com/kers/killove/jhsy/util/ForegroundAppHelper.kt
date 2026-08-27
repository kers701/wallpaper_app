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

    /** 通知栏 / 系统界面等，点通知按钮时容易变成「最近使用」，加入黑名单时应跳过 */
    private val ignorePackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.shell",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.permcenter",
        "com.huawei.systemmanager",
        "com.coloros.safecenter",
        "com.oplus.safecenter"
    )

    /**
     * 当前前台应用包名（按最近使用时间）。
     * @param excludeSelf 排除本应用（点通知按钮时本应用常会变成最近使用）
     * @param extraExclude 额外排除的包名
     */
    fun currentForegroundPackage(
        context: Context,
        excludeSelf: Boolean = true,
        extraExclude: Set<String> = emptySet()
    ): String? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            // 拉长窗口，避免通知交互把真实前台顶掉后找不到候选
            val begin = end - 10 * 60_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)
                ?: return null
            val self = context.packageName
            val blocked = buildSet {
                addAll(ignorePackages)
                addAll(extraExclude)
                if (excludeSelf) {
                    add(self)
                    // debug 变体
                    add("$self.debug")
                    if (self.endsWith(".debug")) add(self.removeSuffix(".debug"))
                }
            }
            stats
                .asSequence()
                .filter { it.lastTimeUsed > 0L }
                .filter { !it.packageName.isNullOrBlank() }
                .filter { it.packageName !in blocked }
                .filter { !it.packageName.startsWith("com.android.provider") }
                .filter { !it.packageName.startsWith("com.google.android.apps.nexuslauncher") }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 按最近使用返回若干候选（已排除本应用与系统界面），供通知「加入黑名单」选用。
     */
    fun recentForegroundCandidates(context: Context, limit: Int = 5): List<String> {
        if (!hasUsageAccess(context)) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 10 * 60_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end) ?: return emptyList()
            val self = context.packageName
            val blocked = ignorePackages + self + "$self.debug"
            stats
                .asSequence()
                .filter { it.lastTimeUsed > 0L && !it.packageName.isNullOrBlank() }
                .filter { it.packageName !in blocked }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName as String }
                .distinct()
                .take(limit)
                .toList()
        } catch (_: Exception) {
            emptyList()
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
        // 排除本应用与系统界面，避免通知展开时误判「本应用在前台」导致错误休眠文案
        val fg = currentForegroundPackage(context, excludeSelf = true) ?: return false
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
