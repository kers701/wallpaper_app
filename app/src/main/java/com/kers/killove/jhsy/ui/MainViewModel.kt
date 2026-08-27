package com.kers.killove.jhsy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kers.killove.jhsy.data.local.LocalFallbackStore
import com.kers.killove.jhsy.data.local.WallpaperDatabase
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.ChangeResult
import com.kers.killove.jhsy.domain.TriggerType
import com.kers.killove.jhsy.domain.AvoidanceLocation
import com.kers.killove.jhsy.util.LocationHelper
import com.kers.killove.jhsy.data.translate.KeywordTranslator
import com.kers.killove.jhsy.domain.WallpaperChanger
import com.kers.killove.jhsy.service.WallpaperForegroundService
import com.kers.killove.jhsy.util.SuperServiceController
import com.kers.killove.jhsy.util.ConfigBackup
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import com.kers.killove.jhsy.util.PinSecurity
import com.kers.killove.jhsy.worker.ChangeWallpaperWorker
import com.kers.killove.jhsy.util.ForegroundAppHelper
import android.app.ActivityManager
import java.io.File
import android.content.SharedPreferences
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val dao = WallpaperDatabase.get(app).dao()
    private val api = WallhavenApi()
    private val localStore = LocalFallbackStore(app)
    private val translator = KeywordTranslator()
    private val changer = WallpaperChanger(
        context = app,
        settingsRepo = settingsRepo,
        api = api,
        setter = SystemWallpaperSetter(app),
        dao = dao,
        localStore = localStore
    )

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                refreshJumpTranslation(s)
            }
        }
    }

    private suspend fun refreshJumpTranslation(s: AppSettings) {
        if (s.jumpKeywords.isEmpty() || s.translateProvider.name == "Off") {
            _jumpKeywordsZh.value = emptyMap()
            return
        }
        _jumpKeywordsZh.value = translator.translateList(s.jumpKeywords, s)
    }

    val recent = dao.recent(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow("就绪")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _networkProbe = MutableStateFlow("点击下方按钮检测：本机网络 · Wallhaven · 兜底 API 延迟")
    val networkProbe: StateFlow<String> = _networkProbe.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    private val _jumpKeywordsZh = MutableStateFlow<Map<String, String>>(emptyMap())
    val jumpKeywordsZh: StateFlow<Map<String, String>> = _jumpKeywordsZh.asStateFlow()

    /** 会话内是否已解锁（进程重启后需重新输入 PIN） */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _pinMessage = MutableStateFlow<String?>(null)
    val pinMessage: StateFlow<String?> = _pinMessage.asStateFlow()

    enum class ServiceStatus { Running, Stopped, Abnormal }

    private val _serviceStatus = MutableStateFlow(ServiceStatus.Stopped)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _launcherApps = MutableStateFlow<List<com.kers.killove.jhsy.util.LauncherAppInfo>>(emptyList())
    val launcherApps: StateFlow<List<com.kers.killove.jhsy.util.LauncherAppInfo>> = _launcherApps.asStateFlow()

    private val prefs: SharedPreferences =
        getApplication<Application>().getSharedPreferences("jhsy_meta", Context.MODE_PRIVATE)

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun finishOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingDone.value = true
    }

    /** 敏感字段是否可见：密钥 / 关键词 / 兜底 API */
    fun keysVisible(s: AppSettings = settings.value): Boolean {
        if (!s.pinEnabled || s.pinHash.isBlank()) return true
        return _unlocked.value
    }

    fun saveSettings(s: AppSettings) {
        viewModelScope.launch {
            // 锁定状态下不允许改写敏感字段
            val final = if (!keysVisible(s) && s.pinEnabled) {
                s.copy(
                    apiKeys = settings.value.apiKeys,
                    keywords = settings.value.keywords,
                    keywordsRemoteUrl = settings.value.keywordsRemoteUrl,
                    fallbackApiUrl = settings.value.fallbackApiUrl,
                    jumpKeywords = settings.value.jumpKeywords
                )
            } else s
            settingsRepo.save(final)
            applySchedule(final)
            _status.value =
                "设置已保存（关键词 ${final.keywords.size} 个，跃迁 ${final.jumpKeywords.size} 个，密钥 ${final.apiKeys.size} 个）"
        }
    }


    /** 备份配置到应用专属目录，并复制 JSON 到剪贴板。不含 PIN。 */
    fun backupConfig() {
        viewModelScope.launch {
            try {
                val s = settings.value
                val file = ConfigBackup.writeToFile(getApplication(), s)
                val json = ConfigBackup.toJson(s)
                val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("jhsy_config", json))
                _status.value = "配置已备份（不含 PIN）\n${file.absolutePath}\n并已复制到剪贴板"
            } catch (e: Exception) {
                _status.value = "备份失败：${e.message}"
            }
        }
    }

    /** 从默认备份文件恢复；保留当前 PIN。 */
    fun restoreConfigFromFile() {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val file = ConfigBackup.defaultFile(ctx)
                if (!file.exists()) {
                    _status.value = "备份文件不存在\n${file.absolutePath}\n请先点「备份配置」或使用「从 JSON 恢复」"
                    return@launch
                }
                val base = settings.value
                val restored = ConfigBackup.readFromFile(ctx, base, file)
                settingsRepo.save(restored)
                applySchedule(restored)
                _status.value = "配置已从文件恢复（PIN 未改动）\n${file.absolutePath}"
            } catch (e: Exception) {
                e.printStackTrace()
                _status.value = "恢复失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** 从任意路径/内容恢复（SAF 选文件后调用） */
    fun restoreConfigFromPath(path: String) {
        viewModelScope.launch {
            try {
                val f = File(path)
                if (!f.exists() || !f.canRead()) {
                    _status.value = "无法读取：$path"
                    return@launch
                }
                val restored = ConfigBackup.fromJson(f.readText(Charsets.UTF_8), settings.value)
                settingsRepo.save(restored)
                applySchedule(restored)
                _status.value = "已从所选文件恢复（PIN 未改动）"
            } catch (e: Exception) {
                _status.value = "恢复失败：${e.message}"
            }
        }
    }

    fun restoreConfigFromUriText(text: String) {
        restoreConfigFromJson(text)
    }

    fun backupFilePath(): String = ConfigBackup.defaultFile(getApplication()).absolutePath

    fun backupConfigToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = ConfigBackup.toJson(settings.value)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入所选位置")
                // 同时写默认文件
                ConfigBackup.writeToFile(getApplication(), settings.value)
                _status.value = "已备份到所选公共位置（不含 PIN）"
            } catch (e: Exception) {
                _status.value = "备份失败：${e.message}"
            }
        }
    }

    fun restoreConfigFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { ins ->
                    BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
                } ?: throw IllegalStateException("无法读取所选文件")
                val restored = ConfigBackup.fromJson(text, settings.value)
                settingsRepo.save(restored)
                applySchedule(restored)
                _status.value = "已从所选文件恢复（PIN 未改动）"
            } catch (e: Exception) {
                _status.value = "恢复失败：${e.message}"
            }
        }
    }


    /** 从粘贴的 JSON 恢复；保留当前 PIN。 */
    fun restoreConfigFromJson(json: String) {
        if (json.isBlank()) {
            _status.value = "请先粘贴备份 JSON"
            return
        }
        viewModelScope.launch {
            try {
                val base = settings.value
                val restored = ConfigBackup.fromJson(json, base)
                settingsRepo.save(restored)
                applySchedule(restored)
                _status.value = "配置已从 JSON 恢复（PIN 未改动）"
            } catch (e: Exception) {
                _status.value = "恢复失败：${e.message}"
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value.copy(enabled = enabled)
            settingsRepo.save(current)
            applySchedule(current)
            _status.value = if (enabled) "已开启自动更换" else "已停止"
        }
    }

    fun changeNow() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = "正在更换…"
            when (val r = changer.changeOnce(forceIgnoreScreenOff = true, triggerType = TriggerType.Manual)) {
                is ChangeResult.Success -> {
                    val res = if (r.item.width > 0) "${r.item.width}×${r.item.height}" else ""
                    val extra = if (r.item.source == "local" && r.item.category.startsWith("local←")) {
                        " · 原因：${r.item.category.removePrefix("local←")}"
                    } else ""
                    val d = if (r.detail.isNotBlank()) " · ${r.detail}" else ""
                    _status.value = "已设置 [${r.item.source}] ${r.item.id} $res$extra$d"
                }
                is ChangeResult.Failure ->
                    _status.value = "失败：${r.message}"
            }
            _busy.value = false
        }
    }

    fun importKeywordsFromUrl(url: String, replace: Boolean = true) {
        if (url.isBlank()) {
            _status.value = "请填写远程关键词 URL"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _status.value = "正在导入关键词…"
            try {
                val remote = api.fetchRemoteKeywordList(url.trim())
                if (remote.isEmpty()) {
                    _status.value = "远程列表为空"
                } else {
                    val merged = if (replace) remote
                    else (settings.value.keywords + remote).distinct()
                    val next = settings.value.copy(
                        keywords = merged,
                        keywordsRemoteUrl = url.trim()
                    )
                    settingsRepo.save(next)
                    _status.value = "已导入 ${remote.size} 个关键词"
                }
            } catch (e: Exception) {
                _status.value = "导入失败：${e.message}"
            }
            _busy.value = false
        }
    }

    fun localFallbackInfo(): String {
        val s = settings.value
        val dir = localStore.resolveDir(s)
        val n = localStore.listImages(s).size
        return "${dir.absolutePath}（${n} 张图）"
    }

    fun unlock(pin: String) {
        val s = settings.value
        if (!s.pinEnabled || s.pinHash.isBlank()) {
            _unlocked.value = true
            _pinMessage.value = null
            return
        }
        if (PinSecurity.verify(pin, s.pinHash)) {
            _unlocked.value = true
            _pinMessage.value = "已解锁"
            _status.value = "PIN 解锁成功"
        } else {
            _pinMessage.value = "PIN 错误"
        }
    }

    fun lockNow() {
        _unlocked.value = false
        _pinMessage.value = "已锁定"
        _status.value = "已锁定，密钥/关键词/兜底 API 已隐藏"
    }

    fun setPinWithConfirm(newPin: String, confirmPin: String) {
        if (newPin != confirmPin) {
            _pinMessage.value = "两次 PIN 不一致"
            return
        }
        setPin(newPin, enable = true)
    }

    fun setPin(newPin: String, enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                if (!PinSecurity.isValidPinFormat(newPin)) {
                    _pinMessage.value = "PIN 需为 4～8 位数字"
                    return@launch
                }
                val next = settings.value.copy(
                    pinEnabled = true,
                    pinHash = PinSecurity.hash(newPin)
                )
                settingsRepo.save(next)
                _unlocked.value = true
                _pinMessage.value = "PIN 已设置"
                _status.value = "PIN 已启用"
            } else {
                if (settings.value.pinEnabled && !_unlocked.value) {
                    _pinMessage.value = "请先解锁再关闭 PIN"
                    return@launch
                }
                val next = settings.value.copy(pinEnabled = false, pinHash = "")
                settingsRepo.save(next)
                _unlocked.value = true
                _pinMessage.value = "PIN 已关闭"
                _status.value = "PIN 已关闭"
            }
        }
    }


    fun testNetwork() {
        if (_probing.value) return
        viewModelScope.launch {
            _probing.value = true
            _networkProbe.value = "检测中…"
            val s = settings.value
            val lines = mutableListOf<String>()

            // 本机网络
            val cm = getApplication<Application>()
                .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val hasNet = caps?.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true
            val validated = caps?.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true
            lines += if (hasNet) {
                "本机网络：已连接" + if (validated) "（已验证）" else "（未验证）"
            } else {
                "本机网络：不可用"
            }

            // Wallhaven
            val wh = api.probeWallhaven(s.nextApiKey())
            lines += if (wh.ok) {
                "Wallhaven：${wh.latencyMs} ms · ${wh.detail}"
            } else {
                "Wallhaven：失败 ${wh.latencyMs} ms · ${wh.detail}"
            }

            // 兜底 API（支持多行多个）
            val fbList = s.fallbackApiUrls()
            if (fbList.isEmpty()) {
                lines += "兜底 API：未配置有效 URL"
            } else {
                lines += "兜底 API：共 ${fbList.size} 个"
                for ((i, raw) in fbList.withIndex()) {
                    val url = raw
                        .replace("{width}", s.minWidth.toString())
                        .replace("{height}", s.minHeight.toString())
                        .replace("{w}", s.minWidth.toString())
                        .replace("{h}", s.minHeight.toString())
                    val r = api.probeUrl("兜底#${i + 1}", url)
                    lines += if (r.ok) {
                        "  #${i + 1}：${r.latencyMs} ms · ${r.detail}"
                    } else {
                        "  #${i + 1}：失败 ${r.latencyMs} ms · ${r.detail}"
                    }
                }
            }

            // 背景 API（若与任一兜底不同）
            val bg = s.bgApiUrl.trim()
            if (bg.isNotBlank() && bg !in fbList) {
                val r = api.probeUrl("背景 API", bg)
                lines += if (r.ok) {
                    "背景 API：${r.latencyMs} ms · ${r.detail}"
                } else {
                    "背景 API：失败 ${r.latencyMs} ms · ${r.detail}"
                }
            }

            _networkProbe.value = lines.joinToString("\n")
            _probing.value = false
        }
    }


    fun setOverviewMinimal(enabled: Boolean) {
        viewModelScope.launch {
            val next = settings.value.copy(overviewMinimalMode = enabled)
            settingsRepo.save(next)
            _status.value = if (enabled) "已开启极简模式" else "已关闭极简模式"
        }
    }

    fun setBlacklist(packages: List<String>) {
        viewModelScope.launch {
            val next = settings.value.copy(blacklistPackages = packages.distinct())
            settingsRepo.save(next)
            _status.value = "黑名单已更新（${packages.size} 个应用）"
        }
    }

    fun toggleBlacklistPackage(pkg: String) {
        val cur = settings.value.blacklistPackages.toMutableList()
        if (pkg in cur) cur.remove(pkg) else cur.add(pkg)
        setBlacklist(cur)
    }

    fun loadLauncherApps() {
        viewModelScope.launch {
            _launcherApps.value = ForegroundAppHelper.listLaunchableApps(getApplication())
        }
    }

    fun refreshServiceStatus() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val s = settings.value
            val enabled = s.enabled || s.superServiceEnabled
            // :svc 独立进程：getRunningServices 在新系统上几乎永远看不到，改查进程名
            val svcAlive = isSvcProcessAlive(ctx)
            // 未开 FGS/超级服务时，仅靠 WorkManager 也算「调度正常」
            val expectsDedicatedProcess = s.useForegroundService || s.superServiceEnabled
            _serviceStatus.value = when {
                !enabled -> ServiceStatus.Stopped
                expectsDedicatedProcess && svcAlive -> ServiceStatus.Running
                expectsDedicatedProcess && !svcAlive -> {
                    // 尝试拉起后再判一次
                    try { WallpaperForegroundService.start(ctx) } catch (_: Exception) {}
                    if (isSvcProcessAlive(ctx)) ServiceStatus.Running
                    else ServiceStatus.Abnormal
                }
                // 仅 Worker / 已开启自动：视为运行中（后台受限不标异常）
                enabled -> ServiceStatus.Running
                else -> ServiceStatus.Stopped
            }
        }
    }

    private fun isSvcProcessAlive(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pkg = ctx.packageName
            val names = setOf("$pkg:svc", pkg)
            am.runningAppProcesses?.any { p ->
                p.processName == "$pkg:svc" ||
                    (p.processName.endsWith(":svc") && p.processName.startsWith(pkg.substringBefore(".debug")))
            } == true
        } catch (_: Exception) {
            false
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            try {
                val dir = File(getApplication<Application>().filesDir, "wallpapers")
                var sum = 0L
                if (dir.isDirectory) {
                    dir.walkTopDown().forEach { if (it.isFile) sum += it.length() }
                }
                _cacheBytes.value = sum
            } catch (_: Exception) {
                _cacheBytes.value = 0L
            }
        }
    }

    fun openUsageAccessSettings() {
        ForegroundAppHelper.openUsageAccessSettings(getApplication())
    }

    fun hasUsageAccess(): Boolean = ForegroundAppHelper.hasUsageAccess(getApplication())



    fun clearWallpaperCache() {
        viewModelScope.launch {
            try {
                val dir = File(getApplication<Application>().filesDir, "wallpapers")
                var n = 0
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { if (it.isFile) { it.delete(); n++ } }
                }
                val frames = File(getApplication<Application>().cacheDir, "video_frames")
                if (frames.isDirectory) {
                    frames.listFiles()?.forEach { if (it.isFile) { it.delete(); n++ } }
                }
                refreshCacheSize()
                _status.value = "已清空壁纸缓存（$n 个文件）"
            } catch (e: Exception) {
                _status.value = "清空缓存失败：${e.message}"
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                dao.deleteAll()
                _status.value = "已清空更换记录（日志）"
            } catch (e: Exception) {
                _status.value = "清空记录失败：${e.message}"
            }
        }
    }



    fun searchAvoidPlaces(keyword: String, onResult: (List<LocationHelper.PlaceHit>) -> Unit) {
        viewModelScope.launch {
            val key = settings.value.amapApiKey
            val hits = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LocationHelper.searchPlaces(key, keyword)
            }
            onResult(hits)
        }
    }

    fun addAvoidanceLocation(loc: AvoidanceLocation) {
        viewModelScope.launch {
            val cur = settings.value.avoidanceLocations().toMutableList()
            if (cur.none { it.id == loc.id }) cur.add(loc)
            val json = LocationHelper.locationsToJson(cur)
            settingsRepo.save(settings.value.copy(avoidanceLocationsJson = json))
            _status.value = "已加入避让：${loc.name}"
        }
    }

    fun removeAvoidanceLocation(id: String) {
        viewModelScope.launch {
            val cur = settings.value.avoidanceLocations().filter { it.id != id }
            val json = LocationHelper.locationsToJson(cur)
            settingsRepo.save(settings.value.copy(avoidanceLocationsJson = json))
            _status.value = "已移除避让点"
        }
    }

    private fun applySchedule(s: AppSettings) {
        val ctx = getApplication<Application>()
        ProcessBridgePrefs.sync(ctx, s)
        if (!s.enabled && !s.superServiceEnabled) {
            ChangeWallpaperWorker.cancel(ctx)
            WallpaperForegroundService.stop(ctx)
            SuperServiceController.disable(ctx)
            return
        }
        // UI 进程只负责调度；真正换壁纸在 :svc 独立进程
        WallpaperForegroundService.start(ctx)
        ChangeWallpaperWorker.enqueue(ctx, s.intervalMinutes)
        if (s.superServiceEnabled) {
            SuperServiceController.enable(ctx)
        } else {
            SuperServiceController.disable(ctx)
        }
    }
}
