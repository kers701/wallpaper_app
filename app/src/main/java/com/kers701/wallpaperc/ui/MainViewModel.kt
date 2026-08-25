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

    fun saveSettings(s: AppSettings) {
        viewModelScope.launch {
            settingsRepo.save(s)
            applySchedule(s)
            _status.value = "设置已保存（关键词 ${s.keywords.size} 个，密钥 ${s.apiKeys.size} 个）"
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
            when (val r = changer.changeOnce()) {
                is ChangeResult.Success ->
                    _status.value = "已设置 [${r.item.source}] ${r.item.id}"
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
