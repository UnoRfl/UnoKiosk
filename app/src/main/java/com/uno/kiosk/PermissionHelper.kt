package com.uno.kiosk

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils

object PermissionHelper {

    // 1. Accessibility service enabled?
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = ComponentName(ctx, KioskAccessibilityService::class.java).flattenToString()
        val enabled = try {
            Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        } catch (_: Exception) { "" }
        if (enabled.isEmpty()) return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val name = splitter.next()
            if (name.equals(expected, ignoreCase = true)) return true
            // Some OEMs store the short form.
            if (name.contains("KioskAccessibilityService", ignoreCase = true)) return true
        }
        return false
    }

    fun openAccessibilitySettings(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {}
    }

    // 2. Overlay permission?
    fun canDrawOverlays(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else true
    }

    fun openOverlaySettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } catch (_: Exception) {}
        }
    }

    // 3. Set as default Home launcher?
    fun isDefaultHome(ctx: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = ctx.packageManager.resolveActivity(intent, 0) ?: return false
        return resolveInfo.activityInfo?.packageName == ctx.packageName
    }

    fun openHomeSettings(activity: Activity) {
        try {
            // Preferred: system default apps → home
            activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            // Fallback: general settings
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    // 4. Battery optimization exemption?
    fun isBatteryOptimizationIgnored(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } else true
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun openBatteryOptimizationRequest(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } catch (_: Exception) {
                try {
                    activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {}
            }
        }
    }
}
