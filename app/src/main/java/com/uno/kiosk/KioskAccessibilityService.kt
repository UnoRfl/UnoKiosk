package com.uno.kiosk

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class KioskAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.LOCKDOWN, false)) return

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""

        // 1. Always allow our own app (kiosk WebView, settings, dialogs).
        if (pkg == packageName) return

        // 2. Allow soft keyboards / IMEs so users can type in the WebView.
        if (cls.contains("InputMethod", ignoreCase = true) ||
            cls.contains("SoftInput", ignoreCase = true) ||
            cls.contains("Keyboard", ignoreCase = true)) return

        val blockRecents = prefs.getBoolean(Prefs.BLOCK_RECENTS, true)

        // 3. System UI: allow the status bar / notification shade,
        //    but bounce back on recents/task-switcher if that block is on.
        if (pkg == "com.android.systemui") {
            val isRecents = cls.contains("Recents", ignoreCase = true) ||
                cls.contains("TaskSwitcher", ignoreCase = true) ||
                cls.contains("Overview", ignoreCase = true) ||
                cls.contains("TaskView", ignoreCase = true)
            if (isRecents && blockRecents) {
                bounceToKiosk()
            }
            return
        }

        // 4. Launcher-hosted recents (Pixel Launcher, Quickstep, various OEMs).
        val looksLikeLauncherRecents = (
            pkg.contains("launcher", ignoreCase = true) ||
                pkg.contains("nexuslauncher", ignoreCase = true) ||
                pkg.contains("quickstep", ignoreCase = true) ||
                pkg.contains("recents", ignoreCase = true)
            ) && (
            cls.contains("Recents", ignoreCase = true) ||
                cls.contains("TaskView", ignoreCase = true) ||
                cls.contains("Overview", ignoreCase = true)
            )
        if (looksLikeLauncherRecents && blockRecents) {
            bounceToKiosk()
            return
        }

        // 5. Anything else in the foreground while lockdown is on → bounce.
        bounceToKiosk()
    }

    private fun bounceToKiosk() {
        val kioskIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        try {
            startActivity(kioskIntent)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: KioskAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
