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

        // Always allow our own package (kiosk + settings + our dialogs).
        if (pkg == packageName) return

        // Always allow system UI (status bar, quick settings surface, keyboards, dialogs).
        if (pkg == "com.android.systemui") return

        // Allow soft keyboards / IMEs so users can type in the WebView.
        if (cls.contains("InputMethod", ignoreCase = true) ||
            cls.contains("Keyboard", ignoreCase = true) ||
            cls.contains("SoftInput", ignoreCase = true)) return

        // Anything else came to the foreground while lockdown is active → bounce back.
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
            // Some OEMs restrict background activity starts even for a11y services.
            // Fallback: press home. If we're the default launcher, this returns to kiosk.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        // No-op
    }

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
