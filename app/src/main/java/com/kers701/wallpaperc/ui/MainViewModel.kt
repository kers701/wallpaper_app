package com.kers701.wallpaperc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kers701.wallpaperc.data.local.LocalFallbackStore
import com.kers701.wallpaperc.data.local.WallpaperDatabase
import com.kers701.wallpaperc.data.prefs.SettingsRepository
import com.kers701.wallpaperc.data.remote.WallhavenApi
import com.kers701.wallpaperc.data.wallpaper.SystemWallpaperSetter
import com.kers701.wallpaperc.domain.AppSettings
import com.kers701.wallpaperc.domain.ChangeResult
import com.kers701.wallpaperc.domain.WallpaperChanger
import com.kers701.wallpaperc.service.WallpaperForegroundService
import com.kers701.wallpaperc.util.PinSecurity
import com.kers701.wallpaperc.worker.ChangeWallpaperWorker
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

    /** 会话内是否已解锁（进程重启后需重新输入 PIN） */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _pinMessage = MutableStateFlow<String?>(null)
    val pinMessage: StateFlow<String?> = _pinMessage.asStateFlow()

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
            when (val r = changer.changeOnce(forceIgnoreScreenOff = true)) {
                is ChangeResult.Success -> {
                    val res = if (r.item.width > 0) "${r.item.width}×${r.item.height}" else ""
                    val extra = if (r.item.source == "local" && r.item.category.startsWith("local←")) {
                        " · 原因：${r.item.category.removePrefix("local←")}"
                    } else ""
                    _status.value = "已设置 [${r.item.source}] ${r.item.id} $res$extra"
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

            // 兜底 API
            val fb = s.fallbackApiUrl.trim()
            if (fb.isBlank()) {
                lines += "兜底 API：未配置"
            } else {
                val url = fb
                    .replace("{width}", s.minWidth.toString())
                    .replace("{height}", s.minHeight.toString())
                    .replace("{w}", s.minWidth.toString())
                    .replace("{h}", s.minHeight.toString())
                val r = api.probeUrl("兜底 API", url)
                lines += if (r.ok) {
                    "兜底 API：${r.latencyMs} ms · ${r.detail}"
                } else {
                    "兜底 API：失败 ${r.latencyMs} ms · ${r.detail}"
                }
            }

            // 背景 API（若与兜底不同）
            val bg = s.bgApiUrl.trim()
            if (bg.isNotBlank() && bg != fb) {
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

    private fun applySchedule(s: AppSettings) {
        val ctx = getApplication<Application>()
        if (!s.enabled) {
            ChangeWallpaperWorker.cancel(ctx)
            WallpaperForegroundService.stop(ctx)
            return
        }
        if (s.useForegroundService || s.intervalMinutes < 15) {
            ChangeWallpaperWorker.cancel(ctx)
            WallpaperForegroundService.start(ctx)
        } else {
            WallpaperForegroundService.stop(ctx)
            ChangeWallpaperWorker.enqueue(ctx, s.intervalMinutes)
        }
    }
}
