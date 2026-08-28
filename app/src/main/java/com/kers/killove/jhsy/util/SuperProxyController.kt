package com.kers.killove.jhsy.util

import android.content.Context
import android.net.Uri
import com.kers.killove.jhsy.domain.AppSettings
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 超级代理（仅本应用）：
 * - 需 Root 启动用户自定义代理内核（sing-box / Clash Meta / mihomo / Xray 等）
 * - 内核只监听 127.0.0.1:本地端口，不开启 TUN / 透明代理 → 不影响其它 App
 * - 内核/配置通过系统文件选择器导入到 [filesDir]/super_proxy/，无需「所有文件访问」权限
 *
 * 配置优先级：
 * 1. 用户导入/指定的配置文件且存在 → 直接使用
 * 2. 否则若填写了订阅链接 → 自动生成默认 Clash Meta 配置
 * 3. 否则无法启动
 */
object SuperProxyController {

    const val DEFAULT_PORT = 17890
    const val IMPORTED_BIN_NAME = "core_bin"
    const val IMPORTED_CFG_NAME = "user_config.yaml"

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

    data class ResolveResult(
        val configPath: String,
        val source: String, // "user" | "subscription" | "none"
        val message: String
    )

    data class ImportResult(
        val ok: Boolean,
        val path: String = "",
        val message: String
    )

    fun workDir(context: Context): File =
        File(context.applicationContext.filesDir, "super_proxy").also { it.mkdirs() }

    fun pidFile(context: Context): File = File(workDir(context), "core.pid")
    fun logFile(context: Context): File = File(workDir(context), "core.log")
    fun autoConfigFile(context: Context): File = File(workDir(context), "auto_clash.yaml")
    fun importedBinFile(context: Context): File = File(workDir(context), IMPORTED_BIN_NAME)
    fun importedConfigFile(context: Context): File = File(workDir(context), IMPORTED_CFG_NAME)

    /**
     * 从 SAF Uri 复制内核到私有目录，并 chmod + 尝试 chown 为当前应用。
     * 不需要 MANAGE_EXTERNAL_STORAGE。
     */
    fun importBinFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val out = importedBinFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return ImportResult(false, message = "无法读取所选文件")
            if (out.length() < 1024) {
                out.delete()
                return ImportResult(false, message = "文件过小，不像可执行内核（${out.length()} 字节）")
            }
            out.setReadable(true, false)
            out.setExecutable(true, false)
            fixPermsRoot(context, out, executable = true)
            ImportResult(
                true,
                out.absolutePath,
                "已导入内核 ${out.length() / 1024} KB → ${out.name}"
            )
        } catch (e: Exception) {
            ImportResult(false, message = "导入内核失败：${e.message}")
        }
    }

    /**
     * 从 SAF Uri 复制配置到私有目录。
     */
    fun importConfigFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val out = importedConfigFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return ImportResult(false, message = "无法读取所选配置")
            if (out.length() < 8) {
                out.delete()
                return ImportResult(false, message = "配置文件为空")
            }
            out.setReadable(true, false)
            fixPermsRoot(context, out, executable = false)
            // 导入时先按默认端口改成本地专用（启动时会再按用户端口改一次）
            adaptConfigForAppLocal(out, DEFAULT_PORT, workDir(context))
            ImportResult(
                true,
                out.absolutePath,
                "已导入配置并改写为仅本机端口 $DEFAULT_PORT → ${out.name}"
            )
        } catch (e: Exception) {
            ImportResult(false, message = "导入配置失败：${e.message}")
        }
    }

    /** Root 下修正属主与权限，保证 su 启动与 app 可读可执行。 */
    private fun fixPermsRoot(context: Context, file: File, executable: Boolean) {
        // 先尽量用 API 改权限（无 root 时也生效）
        try {
            file.setReadable(true, false)
            file.setWritable(true, true)
            if (executable) {
                file.setExecutable(true, false)
            }
        } catch (_: Exception) {
        }
        if (!RootKeepAlive.hasRoot()) return
        val path = shellQuote(file.absolutePath)
        val dir = shellQuote(file.parentFile?.absolutePath ?: workDir(context).absolutePath)
        val mode = if (executable) "0755" else "0644"
        val uid = android.os.Process.myUid()
        try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                os.writeBytes("mkdir -p $dir\n")
                os.writeBytes("chown -R $uid:$uid $dir 2>/dev/null\n")
                os.writeBytes("chmod $mode $path 2>/dev/null\n")
                // 去掉可能阻碍执行的上下文（部分 ROM）
                os.writeBytes("chcon u:object_r:system_file:s0 $path 2>/dev/null\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            p.waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }

    /** 启动前整目录权限加固。 */
    fun ensureWorkDirPerms(context: Context) {
        val wd = workDir(context)
        val bin = importedBinFile(context)
        try {
            wd.mkdirs()
            wd.setReadable(true, false)
            wd.setWritable(true, true)
            wd.setExecutable(true, false)
        } catch (_: Exception) {
        }
        if (bin.exists()) fixPermsRoot(context, bin, executable = true)
        importedConfigFile(context).takeIf { it.exists() }?.let { fixPermsRoot(context, it, false) }
        if (!RootKeepAlive.hasRoot()) return
        val uid = android.os.Process.myUid()
        val dir = shellQuote(wd.absolutePath)
        try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                os.writeBytes("chown -R $uid:$uid $dir 2>/dev/null\n")
                os.writeBytes("chmod 755 $dir 2>/dev/null\n")
                if (bin.exists()) {
                    os.writeBytes("chmod 755 ${shellQuote(bin.absolutePath)} 2>/dev/null\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            p.waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }

    /**
     * 把用户配置改成「仅本应用」：mixed-port/bind 与 App 端口一致，关 TUN。
     * 避免配置里仍是 7890 或 0.0.0.0 导致端口冲突/全局监听。
     */
    fun adaptConfigForAppLocal(configFile: File, localPort: Int, workDir: File): Boolean {
        if (!configFile.exists()) return false
        return try {
            var text = configFile.readText()
            // mixed-port / port / socks-port
            text = text.replace(Regex("""(?m)^mixed-port:\s*\d+"""), "mixed-port: $localPort")
            text = text.replace(Regex("""(?m)^port:\s*\d+"""), "port: $localPort")
            text = text.replace(Regex("""(?m)^socks-port:\s*\d+"""), "socks-port: $localPort")
            if (!Regex("""(?m)^mixed-port:\s*""").containsMatchIn(text) &&
                !Regex("""(?m)^port:\s*""").containsMatchIn(text)
            ) {
                text = "mixed-port: $localPort\n" + text
            }
            // bind
            if (Regex("""(?m)^bind-address:\s*""").containsMatchIn(text)) {
                text = text.replace(Regex("""(?m)^bind-address:\s*.*"""), "bind-address: 127.0.0.1")
            } else {
                text = text.replace(
                    Regex("""(?m)^mixed-port:\s*\d+"""),
                    "mixed-port: $localPort\nbind-address: 127.0.0.1"
                )
            }
            text = text.replace(Regex("""(?m)^allow-lan:\s*true"""), "allow-lan: false")
            // tun enable false（简单替换常见写法）
            text = text.replace(
                Regex("""(?m)^(\s*)enable:\s*true(\s*#.*)?$"""),
                "$1enable: false$2"
            )
            // 仅在 tun: 块附近误伤风险：再保险把 tun.enable 写死不完美，用户精简配置已是 false
            // external-controller 仅本机
            text = text.replace(
                Regex("""(?m)^external-controller:\s*0\.0\.0\.0:\d+"""),
                "external-controller: 127.0.0.1:0"
            )
            configFile.writeText(text)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 探测内核类型：mihomo/clash / sing-box / xray */
    fun detectCoreKind(binPath: String): String {
        val name = File(binPath).name.lowercase()
        when {
            name.contains("sing-box") || name == "singbox" -> return "sing-box"
            name.contains("xray") -> return "xray"
            name.contains("v2ray") -> return "v2ray"
            name.contains("mihomo") || name.contains("clash") -> return "mihomo"
            name == IMPORTED_BIN_NAME.lowercase() || name == "core_bin" -> {
                // 跑 -v / -h 看输出
                return try {
                    val p = Runtime.getRuntime().exec(arrayOf(binPath, "-v"))
                    val out = p.inputStream.bufferedReader().readText() +
                        p.errorStream.bufferedReader().readText()
                    p.waitFor(2, TimeUnit.SECONDS)
                    val all = out.lowercase()
                    when {
                        "mihomo" in all || "clash" in all || "metacubex" in all -> "mihomo"
                        "sing-box" in all -> "sing-box"
                        "xray" in all -> "xray"
                        else -> "mihomo" // 本功能默认按 mihomo
                    }
                } catch (_: Exception) {
                    "mihomo"
                }
            }
            else -> return "mihomo"
        }
    }

    fun status(context: Context, s: AppSettings): Status {
        val root = RootKeepAlive.hasRoot()
        val bin = s.superProxyBinPath.trim()
        val binOk = bin.isNotEmpty() && File(bin).exists()
        val port = s.superProxyLocalPort.coerceIn(1025, 65535)
        val resolved = resolveConfig(context, s)
        val configOk = resolved.source != "none" && File(resolved.configPath).exists()
        val pid = readPid(context)
        val running = pid != null && isPidAlive(pid)
        val msg = when {
            !s.superProxyEnabled -> "超级代理：未启用（仅本应用，需 Root + 内核）"
            !root -> "超级代理：无 Root，无法启动内核"
            !binOk -> "超级代理：请设置有效的内核路径"
            resolved.source == "none" -> "超级代理：请填订阅链接或自定义配置路径"
            running -> "超级代理：运行中 PID=$pid · 127.0.0.1:$port（${resolved.source}）"
            else -> "超级代理：已启用 · ${resolved.message} · 点击启动"
        }
        return Status(root, s.superProxyEnabled, running, binOk, configOk, port, pid, msg)
    }

    /**
     * 解析最终配置路径。
     * 优先级：用户自定义配置文件 > 订阅链接自动生成 Clash 默认配置。
     */
    fun resolveConfig(context: Context, s: AppSettings): ResolveResult {
        val userCfg = s.superProxyConfigPath.trim()
        if (userCfg.isNotEmpty() && File(userCfg).exists()) {
            return ResolveResult(userCfg, "user", "使用用户配置：$userCfg")
        }
        val sub = s.superProxySubUrl.trim()
        if (sub.isNotEmpty() && (sub.startsWith("http://") || sub.startsWith("https://"))) {
            val port = s.superProxyLocalPort.coerceIn(1025, 65535)
            val out = autoConfigFile(context)
            return try {
                writeClashSubscriptionConfig(out, sub, port)
                ResolveResult(
                    out.absolutePath,
                    "subscription",
                    "已由订阅生成默认 Clash 配置 → ${out.name}"
                )
            } catch (e: Exception) {
                ResolveResult("", "none", "生成默认配置失败：${e.message}")
            }
        }
        if (userCfg.isNotEmpty()) {
            return ResolveResult(
                userCfg,
                "none",
                "自定义配置不存在：$userCfg（可改填订阅链接自动生成）"
            )
        }
        return ResolveResult("", "none", "未设置配置：请填订阅链接或有效配置路径")
    }

    /**
     * 默认 Clash / Clash Meta 配置：
     * - mixed-port 绑定 127.0.0.1（HTTP+SOCKS，本应用连 SOCKS5）
     * - 无 TUN / 无透明代理
     * - proxy-providers 拉取订阅，自动节点
     */
    fun writeClashSubscriptionConfig(out: File, subscriptionUrl: String, localPort: Int) {
        val yaml = """
            # Auto-generated by wallpaper_app SuperProxy — do not edit while running
            # Priority: user config path overrides this file
            mixed-port: $localPort
            bind-address: 127.0.0.1
            allow-lan: false
            mode: rule
            log-level: warning
            ipv6: false
            external-controller: ""

            dns:
              enable: true
              enhanced-mode: fake-ip
              nameserver:
                - 1.1.1.1
                - 8.8.8.8

            proxy-providers:
              sub:
                type: http
                url: "${subscriptionUrl.replace("\"", "\\\"")}"
                interval: 3600
                path: ./provider_sub.yaml
                health-check:
                  enable: true
                  url: http://www.gstatic.com/generate_204
                  interval: 600

            proxies: []

            proxy-groups:
              - name: PROXY
                type: select
                use:
                  - sub
                proxies:
                  - DIRECT

            rules:
              - MATCH,PROXY
        """.trimIndent() + "\n"
        out.parentFile?.mkdirs()
        out.writeText(yaml)
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
     * 启动前自动 resolveConfig（用户配置优先，否则订阅生成默认配置）。
     */
    fun start(context: Context, s: AppSettings): String? {
        if (!RootKeepAlive.hasRoot()) return "无 Root 权限"
        val bin = s.superProxyBinPath.trim()
        if (bin.isEmpty() || !File(bin).exists()) return "内核文件不存在：$bin"

        val resolved = resolveConfig(context, s)
        if (resolved.source == "none" || resolved.configPath.isBlank()) {
            return resolved.message.ifBlank { "请填订阅链接或自定义配置路径" }
        }
        val cfg = resolved.configPath
        if (!File(cfg).exists()) return "配置文件不存在：$cfg"

        val port = s.superProxyLocalPort.coerceIn(1025, 65535)
        val wdFile = workDir(context)
        val wd = wdFile.absolutePath
        val log = logFile(context).absolutePath
        val pidPath = pidFile(context).absolutePath

        // 配置改写：强制 mixed-port / bind 127.0.0.1，避免仍用 7890 冲突
        adaptConfigForAppLocal(File(cfg), port, wdFile)
        ensureWorkDirPerms(context)
        fixPermsRoot(context, File(bin), executable = true)

        stop(context)

        val kind = detectCoreKind(bin)
        // 用户若误填 mihomo 不支持的 -c，自动改回 -f/-d
        val rawArgs = s.superProxyArgs.trim()
        val argsTemplate = when {
            rawArgs.isBlank() -> defaultArgsFor(bin, kind)
            kind == "mihomo" && rawArgs.contains("-c") && !rawArgs.contains("-f") ->
                defaultArgsFor(bin, kind)
            kind == "mihomo" && !rawArgs.contains("-d") && rawArgs.contains("-f") ->
                rawArgs + " -d {workdir}"
            else -> rawArgs
        }
        val args = argsTemplate
            .replace("{bin}", shellQuote(bin))
            .replace("{config}", shellQuote(cfg))
            .replace("{port}", port.toString())
            .replace("{workdir}", shellQuote(wd))

        val runCmd = if (argsTemplate.contains("{bin}")) {
            args
        } else {
            "${shellQuote(bin)} $args"
        }

        // HOME/XDG 指到工作目录，避免写到 /.config/mihomo（只读根分区）
        val script = """
            chmod 755 ${shellQuote(bin)} 2>/dev/null
            mkdir -p ${shellQuote(wd)}
            chown ${android.os.Process.myUid()}:${android.os.Process.myUid()} ${shellQuote(wd)} 2>/dev/null
            cd ${shellQuote(wd)} || exit 1
            export HOME=${shellQuote(wd)}
            export XDG_CONFIG_HOME=${shellQuote(wd)}
            : > ${shellQuote(log)}
            chmod 666 ${shellQuote(log)} 2>/dev/null
            echo "KIND: $kind" >> ${shellQuote(log)}
            echo "CMD: $runCmd" >> ${shellQuote(log)}
            echo "PORT: $port CFG: $cfg" >> ${shellQuote(log)}
            nohup $runCmd >>${shellQuote(log)} 2>&1 &
            echo ${'$'}! > ${shellQuote(pidPath)}
            chmod 666 ${shellQuote(pidPath)} 2>/dev/null
            chmod 666 ${shellQuote(log)} 2>/dev/null
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
            val finished = p.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                return "su 命令超时（请确认已授权 Root）"
            }
            // mihomo 拉订阅可能稍慢，多等一会儿再判定
            Thread.sleep(1200)
            val pid = readPid(context)
            if (pid != null && isPidAlive(pid)) null
            else {
                val tail = runCatching {
                    logFile(context).readText().takeLast(800)
                }.getOrDefault("(无日志，可能无 Root 权限或路径不可写)")
                "启动失败或进程已退出（配置来源=${resolved.source}，cfg=$cfg）。\n$tail"
            }
        } catch (e: Exception) {
            "启动异常：${e.message}"
        }
    }

    fun stop(context: Context): Boolean {
        val pid = readPid(context)
        if (!RootKeepAlive.hasRoot()) {
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

    /** 根据二进制名 / 探测结果猜默认参数（导入的 core_bin 默认按 mihomo） */
    fun defaultArgsFor(binPath: String, kind: String = detectCoreKind(binPath)): String {
        return when (kind) {
            "sing-box" -> "run -c {config}"
            "xray", "v2ray" -> "run -c {config}"
            else -> "-f {config} -d {workdir}" // mihomo / Clash Meta
        }
    }

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"
}
