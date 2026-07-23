package com.uno.kiosk

object Prefs {
    const val NAME = "uno_kiosk_prefs"

    // Core
    const val URL = "url"
    const val PIN = "pin"
    const val LOCKDOWN = "lockdown_active"

    // Standby: set by Full Exit. When true, the app behaves like a normal
    // background app until the user hits Reactivate. Permissions are never
    // touched — they stay granted.
    const val EXITED = "app_exited"

    // Floating button position
    const val FAB_X = "fab_x"
    const val FAB_Y = "fab_y"

    // "What to block"
    const val BLOCK_RECENTS = "block_recents"
    const val BLOCK_NOTIFICATIONS = "block_notifications"
    const val BLOCK_VOLUME = "block_volume"
    const val BLOCK_SCREENSHOTS = "block_screenshots"
    const val IMMERSIVE = "immersive"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val SHOW_FLOATING_BUTTON = "show_floating_button"
    const val AUTO_RELOAD_ON_ERROR = "auto_reload_on_error"

    fun defaults(prefs: android.content.SharedPreferences) {
        if (prefs.contains(BLOCK_RECENTS)) return
        prefs.edit()
            .putBoolean(BLOCK_RECENTS, true)
            .putBoolean(BLOCK_NOTIFICATIONS, true)
            .putBoolean(BLOCK_VOLUME, false)
            .putBoolean(BLOCK_SCREENSHOTS, false)
            .putBoolean(IMMERSIVE, true)
            .putBoolean(KEEP_SCREEN_ON, true)
            .putBoolean(SHOW_FLOATING_BUTTON, true)
            .putBoolean(AUTO_RELOAD_ON_ERROR, true)
            .apply()
    }
}
