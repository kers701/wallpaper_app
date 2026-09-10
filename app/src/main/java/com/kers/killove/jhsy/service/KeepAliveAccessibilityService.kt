package com.kers.killove.jhsy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.kers.killove.jhsy.util.ProcessBridgePrefs

/**
 * 无障碍保活：系统对已开启无障碍的应用杀进程更保守。
 * 不采集界面内容，仅在连接时拉起独立进程中的更换服务。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 0
        }
        if (ProcessBridgePrefs.enabled(this) || ProcessBridgePrefs.superService(this)) {
            WallpaperForegroundService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理任何事件，避免耗电与隐私问题
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }
}
