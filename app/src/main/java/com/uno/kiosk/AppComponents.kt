package com.uno.kiosk

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Toggles manifest components so we can "unregister" ourselves as a home
 * launcher and boot receiver on Full Exit, and re-register on lockdown.
 */
object AppComponents {

    private const val HOME_ALIAS = ".HomeAlias"
    private const val BOOT_RECEIVER = ".BootReceiver"

    fun enableKioskComponents(ctx: Context) {
        setEnabled(ctx, HOME_ALIAS, true)
        setEnabled(ctx, BOOT_RECEIVER, true)
    }

    fun disableKioskComponents(ctx: Context) {
        setEnabled(ctx, HOME_ALIAS, false)
        setEnabled(ctx, BOOT_RECEIVER, false)
    }

    private fun setEnabled(ctx: Context, relativeName: String, enabled: Boolean) {
        val comp = ComponentName(ctx.packageName, ctx.packageName + relativeName)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            ctx.packageManager.setComponentEnabledSetting(
                comp, newState, PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
            // Silently ignore — some OEMs deny changing component states.
        }
    }
}
