package com.kers.killove.jhsy.util

import android.content.Context
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root 保活：在有 su 时写入一个简单的重启脚本并尝试后台循环拉起服务。
 * 无 Root 时 hasRoot()=false，不执行任何操作。
 */
object RootKeepAlive {

    fun hasRoot(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/data/local/su", "/data/local/bin/su"
        )
        if (paths.any { File(it).exists() }) {
            return tryExec("id")
        }
        return tryExec("su -c id")
    }

    private fun tryExec(cmd: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val ok = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        p.destroy()
        ok
    } catch (_: Exception) {
        false
    }

    /** 启动 root 侧循环：每 60 秒确保服务进程在 */
    fun startDaemon(context: Context): Boolean {
        if (!hasRoot()) return false
        val pkg = context.packageName
        val component = "$pkg/.service.WallpaperForegroundService"
        val script = """
            while true; do
              am start-foreground-service -n $component >/dev/null 2>&1 || am startservice -n $component >/dev/null 2>&1
              sleep 60
            done
        """.trimIndent()
        return try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                os.writeBytes("nohup sh -c '${script.replace("'", "'\\''")}' >/dev/null 2>&1 &\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            p.waitFor(3, TimeUnit.SECONDS)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopDaemon(): Boolean {
        if (!hasRoot()) return false
        return try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                os.writeBytes("pkill -f 'am start-foreground-service -n.*WallpaperForegroundService' 2>/dev/null\n")
                os.writeBytes("pkill -f 'jhsy_root_keepalive' 2>/dev/null\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            p.waitFor(2, TimeUnit.SECONDS)
            true
        } catch (_: Exception) {
            false
        }
    }
}
