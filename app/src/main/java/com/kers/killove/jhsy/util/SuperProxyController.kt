package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 超级代理（仅本应用）：
 * - 需 Root 启动用户自定义代理内核（sing-box / Clash Meta / Xray 等）
 * - 内核只监听 127.0.0.1:本地端口，不开启 TUN / 透明代理 → 不影响其它 App
 * - 本应用通过 ProxyHttp 连接该本地端口即可
 */
object SuperProxyController {

    const val DEFAULT_PORT = 17890

    data class Status(
        val hasRoot: Boolean,
        val enabled: Boolean,
        val running: Boolean,
        val binOk: Boolean,
        val configOk: Boolean,
        val localPort: Int,
        val pid: Int?,
        val message: String
    )

    fun workDir(context: Context): File =
        File(context.applicationContext.filesDir, "super_proxy").also { it.mkdirs() }

    fun pidFile(context: Context): File = File(workDir(context), "core.pid")
    fun logFile(context: Context): File = File(workDir(context), "core.log")

    fun status(context: Context, s: AppSettings): Status {
        val root = RootKeepAlive.hasRoot()
        val bin = s.superProxyBinPath.trim()
        val cfg = s.superProxyConfigPath.trim()
        val binOk = bin.isNotEmpty() && File(bin).exists()
        val configOk = cfg.isNotEmpty() && File(cfg).exists()
        val port = s.superProxyLocalPort.coerceIn(1025, 65535)
        val pid = readPid(context)
        val running = pid != null && isPidAlive(pid)
        val msg = when {
            !s.superProxyEnabled -> "超级代理：未启用（仅本应用，需 Root + 内核）"
            !root -> "超级代理：无 Root，无法启动内核"
            !binOk -> "超级代理：请设置有效的内核路径"
            !configOk -> "超级代理：请设置有效的配置文件路径"
            running -> "超级代理：运行中 PID=$pid · 127.0.0.1:$port（仅本应用）"
            else -> "超级代理：已启用但内核未在运行，请点击启动"
        }
        return Status(root, s.superProxyEnabled, running, binOk, configOk, port, pid, msg)
    }

    fun readPid(context: Context): Int? = try {
        val f = pidFile(context)
        if (!f.exists()) null
        else f.readText().trim().toIntOrNull()
    } catch (_: Exception) {
        null
    }

    fun isPidAlive(pid: Int): Boolean {
        if (pid <= 0) return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -0 $pid 2>/dev/null"))
            val ok = p.waitFor(1, TimeUnit.SECONDS) && p.exitValue() == 0
            p.destroy()
            ok
        } catch (_: Exception) {
            // 无权限时用 su
            try {
                val p = Runtime.getRuntime().exec("su")
                DataOutputStream(p.outputStream).use { os ->
                    os.writeBytes("kill -0 $pid 2>/dev/null\n")
                    os.writeBytes("exit\n")
                    os.flush()
                }
                p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 启动内核。成功返回 null，失败返回错误文案。
     * 启动参数支持占位符：{bin} {config} {port} {workdir}
     */
    fun start(context: Context, s: AppSettings): String? {
        if (!RootKeepAlive.hasRoot()) return "无 Root 权限"
        val bin = s.superProxyBinPath.trim()
        val cfg = s.superProxyConfigPath.trim()
        if (bin.isEmpty() || !File(bin).exists()) return "内核文件不存在：$bin"
        if (cfg.isEmpty() || !File(cfg).exists()) return "配置文件不存在：$cfg"
        val port = s.superProxyLocalPort.coerceIn(1025, 65535)
        val wd = workDir(context).absolutePath
        val log = logFile(context).absolutePath
        val pidPath = pidFile(context).absolutePath

        // 已在跑则先停
        stop(context)

        val argsTemplate = s.superProxyArgs.ifBlank { defaultArgsFor(bin) }
        val args = argsTemplate
            .replace("{bin}", shellQuote(bin))
            .replace("{config}", shellQuote(cfg))
            .replace("{port}", port.toString())
            .replace("{workdir}", shellQuote(wd))

        // 完整命令：chmod + 后台启动 + 写 pid
        // 注意：{bin} 已在 args 里时可能重复，args 模板通常不含 bin 路径时用单独 exec
        val runCmd = if (argsTemplate.contains("{bin}")) {
            args
        } else {
            "${shellQuote(bin)} $args"
        }

        val script = """
            chmod 755 ${shellQuote(bin)} 2>/dev/null
            cd ${shellQuote(wd)} || exit 1
            nohup $runCmd >${shellQuote(log)} 2>&1 &
            echo ${'$'}! > ${shellQuote(pidPath)}
        """.trimIndent()

        return try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                script.lines().forEach { line ->
                    os.writeBytes(line + "\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            p.waitFor(5, TimeUnit.SECONDS)
            Thread.sleep(400)
            val pid = readPid(context)
            if (pid != null && isPidAlive(pid)) null
            else {
                val tail = runCatching {
                    logFile(context).readText().takeLast(400)
                }.getOrDefault("")
                "启动失败或进程已退出。日志尾部：$tail"
            }
        } catch (e: Exception) {
            "启动异常：${e.message}"
        }
    }

    fun stop(context: Context): Boolean {
        val pid = readPid(context)
        if (!RootKeepAlive.hasRoot()) {
            // 无 root 仍尝试普通 kill
            if (pid != null) {
                try {
                    Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString())).waitFor(1, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
            }
            runCatching { pidFile(context).delete() }
            return true
        }
        return try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                if (pid != null) {
                    os.writeBytes("kill $pid 2>/dev/null\n")
                    os.writeBytes("kill -9 $pid 2>/dev/null\n")
                }
                // 兜底：按工作目录日志特征不强杀其它进程
                os.writeBytes("exit\n")
                os.flush()
            }
            p.waitFor(3, TimeUnit.SECONDS)
            runCatching { pidFile(context).delete() }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 根据二进制名猜默认参数 */
    fun defaultArgsFor(binPath: String): String {
        val name = File(binPath).name.lowercase()
        return when {
            name.contains("sing-box") || name == "singbox" ->
                "run -c {config}"
            name.contains("xray") ->
                "run -c {config}"
            name.contains("clash") ->
                "-f {config} -d {workdir}"
            name.contains("v2ray") ->
                "run -c {config}"
            else ->
                "-c {config}"
        }
    }

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"
}
