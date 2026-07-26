package com.uno.kiosk

import android.os.SystemClock

/**
 * While the kiosk is locked down, KioskAccessibilityService bounces any
 * foreground app that isn't us back to the kiosk. That's exactly wrong when
 * the WEB APP itself asked for the camera or a file picker — those run in
 * other packages (com.android.camera, DocumentsUI, Google Photos, ...) and
 * would get killed the instant they appear.
 *
 * PickerGuard is a short-lived "the user is legitimately in a system picker
 * right now" window. It can only be opened from inside our own WebView in
 * response to a file-input tap, so a customer can't use it to escape.
 *
 * It always expires on its own, so a crashed or force-closed picker can never
 * leave the kiosk permanently unlocked.
 */
object PickerGuard {

    // Generous: photographing a probe display, focusing, retaking, and
    // confirming can easily take a minute on a slow phone.
    private const val DEFAULT_WINDOW_MS = 180_000L

    @Volatile
    private var activeUntil = 0L

    fun begin(windowMs: Long = DEFAULT_WINDOW_MS) {
        activeUntil = SystemClock.elapsedRealtime() + windowMs
    }

    fun end() {
        activeUntil = 0L
    }

    val isActive: Boolean
        get() = SystemClock.elapsedRealtime() < activeUntil
}
