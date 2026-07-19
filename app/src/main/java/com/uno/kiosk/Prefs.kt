package com.uno.kiosk

object Prefs {
    const val NAME = "uno_kiosk_prefs"

    // Core
    const val URL = "url"
    const val PIN = "pin"
    const val LOCKDOWN = "lockdown_active"

    // Floating button position
    const val FAB_X = "fab_x"
    const val FAB_Y = "fab_y"

    // "What to block" — user-toggled personalization
    const val BLOCK_RECENTS = "block_recents"           // via accessibility service
    const val BLOCK_NOTIFICATIONS = "block_notifications" // via overlay
    const val BLOCK_VOLUME = "block_volume"             // consume key events
    const val BLOCK_SCREENSHOTS = "block_screenshots"   // FLAG_SECURE
    const val IMMERSIVE = "immersive"                   // hide status + nav
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val SHOW_FLOATING_BUTTON = "show_floating_button"
    const val AUTO_RELOAD_ON_ERROR = "auto_reload_on_error"

    // Defaults
    fun defaults(prefs: android.content.SharedPreferences) {
        // If this is the first launch, seed defaults so toggles are enabled.
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
