package com.uno.kiosk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.system.exitProcess

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // Permission rows
    private lateinit var permHome: View
    private lateinit var permAcc: View
    private lateinit var permOverlay: View
    private lateinit var permBattery: View

    // Banners
    private lateinit var standbyBanner: View
    private lateinit var lockdownCard: View
    private lateinit var lockdownBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        Prefs.defaults(prefs)

        standbyBanner = findViewById(R.id.standby_banner)
        lockdownCard = findViewById(R.id.lockdown_card)

        setupStandbyBanner()
        setupUrlPin()
        setupPermissionRows()
        setupBlockSwitches()
        setupBehaviorSwitches()
        setupLockdown()
        setupUtilities()
        setupFullExit()
    }

    override fun onResume() {
        super.onResume()
        refreshStandbyVisibility()
        refreshPermissionRows()
        refreshLockdownButton()
    }

    // ============================================================
    // Standby banner (visible only when EXITED=true)
    // ============================================================
    private fun setupStandbyBanner() {
        findViewById<MaterialButton>(R.id.reactivate_btn).setOnClickListener {
            reactivate()
        }
    }

    private fun refreshStandbyVisibility() {
        val exited = prefs.getBoolean(Prefs.EXITED, false)
        standbyBanner.visibility = if (exited) View.VISIBLE else View.GONE
        // When in standby, hide the lockdown card so user reactivates first.
        lockdownCard.visibility = if (exited) View.GONE else View.VISIBLE
    }

    private fun reactivate() {
        prefs.edit().putBoolean(Prefs.EXITED, false).apply()
        Toast.makeText(this, "Reactivated — check permissions below and turn Lockdown ON when ready", Toast.LENGTH_LONG).show()
        refreshStandbyVisibility()
        refreshPermissionRows()
        refreshLockdownButton()
    }

    // ============================================================
    // URL + PIN
    // ============================================================
    private fun setupUrlPin() {
        val urlField = findViewById<EditText>(R.id.url_field)
        val pinField = findViewById<EditText>(R.id.pin_field)
        val saveBtn = findViewById<MaterialButton>(R.id.save_btn)

        urlField.setText(prefs.getString(Prefs.URL, ""))
        pinField.setText(prefs.getString(Prefs.PIN, ""))

        saveBtn.setOnClickListener {
            val url = urlField.text.toString().trim()
            val pin = pinField.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "URL required", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val normalized = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            if (pin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            prefs.edit().putString(Prefs.URL, normalized).putString(Prefs.PIN, pin).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // Permission rows with info tips
    // ============================================================
    private fun setupPermissionRows() {
        permHome = findViewById(R.id.perm_home)
        permAcc = findViewById(R.id.perm_accessibility)
        permOverlay = findViewById(R.id.perm_overlay)
        permBattery = findViewById(R.id.perm_battery)

        bindPermRow(permHome, R.drawable.ic_home, "Home Launcher",
            "Set Uno Kiosk as the default home app so Home button can't leave the kiosk.",
            "Open") { PermissionHelper.openHomeSettings(this) }
        bindTip(permHome, "Home Launcher", homeLauncherTip())

        bindPermRow(permAcc, R.drawable.ic_accessibility, "Accessibility Service",
            "Blocks the app switcher and any other app the customer tries to launch.",
            "Open") { PermissionHelper.openAccessibilitySettings(this) }
        bindTip(permAcc, "Accessibility Service", accessibilityTip())

        bindPermRow(permOverlay, R.drawable.ic_layers, "Display over other apps",
            "Covers the top edge so the notification shade can't be pulled down.",
            "Open") { PermissionHelper.openOverlaySettings(this) }
        bindTip(permOverlay, "Display over other apps", overlayTip())

        bindPermRow(permBattery, R.drawable.ic_battery, "Battery optimization",
            "Stops Android from killing the app during long idle periods.",
            "Open") { PermissionHelper.openBatteryOptimizationRequest(this) }
        bindTip(permBattery, "Battery optimization", batteryTip())
    }

    private fun bindPermRow(
        row: View, iconRes: Int, title: String, desc: String, actionLabel: String, onGrant: () -> Unit
    ) {
        row.findViewById<ImageView>(R.id.row_icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.row_title).text = title
        row.findViewById<TextView>(R.id.row_desc).text = desc
        val action = row.findViewById<MaterialButton>(R.id.row_action)
        action.text = actionLabel
        action.setOnClickListener { onGrant() }
    }

    private fun bindTip(row: View, title: String, tipHtml: String) {
        row.findViewById<ImageView>(R.id.row_info).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(tipHtml)
                .setPositiveButton("Got it", null)
                .show()
        }
    }

    private fun refreshPermissionRows() {
        setRowStatus(permHome, PermissionHelper.isDefaultHome(this))
        setRowStatus(permAcc, PermissionHelper.isAccessibilityEnabled(this))
        setRowStatus(permOverlay, PermissionHelper.canDrawOverlays(this))
        setRowStatus(permBattery, PermissionHelper.isBatteryOptimizationIgnored(this))
    }

    private fun setRowStatus(row: View, granted: Boolean) {
        val statusPill = row.findViewById<TextView>(R.id.row_status)
        if (granted) {
            statusPill.text = "Granted"
            statusPill.setTextColor(getColor(R.color.neon_green))
            statusPill.setBackgroundResource(R.drawable.status_pill_ok)
        } else {
            statusPill.text = "Not granted"
            statusPill.setTextColor(getColor(R.color.neon_pink))
            statusPill.setBackgroundResource(R.drawable.status_pill_bad)
        }
    }

    // ============================================================
    // Permission tip texts (brand-aware where useful)
    // ============================================================
    private val brand: String get() = (Build.MANUFACTURER ?: "").lowercase()

    private fun brandHint(): String {
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                "\n\nXiaomi/Redmi note: also enable \"USB debugging (Security settings)\" and turn OFF \"MIUI optimization\" in Developer Options for this to work reliably."
            brand.contains("samsung") ->
                "\n\nSamsung note: some options are under Settings → Apps → Default apps or Settings → Apps → 3-dot menu → Special access."
            brand.contains("oppo") || brand.contains("realme") ->
                "\n\nOPPO/Realme note: some permissions live under Settings → App management → App list → Uno Kiosk → Permissions."
            brand.contains("vivo") ->
                "\n\nVivo note: also check Settings → Battery → Background high power consumption."
            brand.contains("huawei") || brand.contains("honor") ->
                "\n\nHuawei/Honor note: also check Settings → Apps → Startup manager to allow background start."
            else -> ""
        }
    }

    private fun homeLauncherTip(): String = """
        WHAT: Makes Uno Kiosk the phone's default home app, so pressing Home returns to the kiosk instead of the normal launcher.

        HOW TO FIND IT:
        1. Tap "Open" — it takes you directly to Android's Home app picker.
        2. Select "Uno Kiosk" from the list.
        3. Tap "Always" if it asks.

        MANUAL PATH (if the button doesn't work):
        Settings → Apps → Default apps → Home app

        NOTE: If you don't see Uno Kiosk in the list, press the Home button once first — Android will show a chooser where you can pick it.
    """.trimIndent() + brandHint()

    private fun accessibilityTip(): String = """
        WHAT: Lets the app detect when other apps come to the foreground so it can bounce the customer back to the kiosk.

        HOW TO FIND IT:
        1. Tap "Open" — takes you to Accessibility settings.
        2. Scroll to "Installed apps" or "Downloaded apps".
        3. Find "Uno Kiosk Lockdown".
        4. Toggle it ON. Confirm the warning dialog.

        ANDROID 13+ WARNING:
        If it says "Restricted setting" and won't let you enable, do this:
        1. Go back to Settings → Apps → Uno Kiosk
        2. Tap the 3-dot menu (top-right) → "Allow restricted settings"
        3. Now retry the Accessibility toggle.

        This is Google's anti-malware guard for sideloaded apps — it's a one-time step.
    """.trimIndent() + brandHint()

    private fun overlayTip(): String = """
        WHAT: Lets the app draw a thin invisible strip over the status bar so the notification shade can't be pulled down.

        HOW TO FIND IT:
        1. Tap "Open" — takes you directly to the Uno Kiosk overlay setting.
        2. Toggle "Allow display over other apps" ON.

        MANUAL PATH:
        Settings → Apps → Special access → Display over other apps → Uno Kiosk → Allow
    """.trimIndent() + brandHint()

    private fun batteryTip(): String = """
        WHAT: Prevents Android from killing the app after long idle periods, which would break the kiosk.

        HOW TO FIND IT:
        1. Tap "Open" — a dialog asks "Allow the app to ignore battery optimizations?"
        2. Tap "Allow".

        MANUAL PATH:
        Settings → Battery → Battery optimization (or "Apps not optimized") → Uno Kiosk → Don't optimize
    """.trimIndent() + brandHint()

    // ============================================================
    // Block + Behavior switches
    // ============================================================
    private fun setupBlockSwitches() {
        bindSwitchRow(R.id.switch_recents, "App Switcher (Recents)",
            "Kicks the customer back to the kiosk if they open the multitasking view.",
            Prefs.BLOCK_RECENTS, true)
        bindSwitchRow(R.id.switch_notifications, "Notification Shade",
            "Covers the status bar with an invisible overlay so it can't be pulled down.",
            Prefs.BLOCK_NOTIFICATIONS, true) { on ->
            if (prefs.getBoolean(Prefs.LOCKDOWN, false)) {
                if (on && PermissionHelper.canDrawOverlays(this)) OverlayService.start(this)
                else OverlayService.stop(this)
            }
        }
        bindSwitchRow(R.id.switch_volume, "Volume Buttons",
            "Ignores hardware volume up/down/mute presses while in the kiosk.",
            Prefs.BLOCK_VOLUME, false)
        bindSwitchRow(R.id.switch_screenshots, "Screenshots",
            "Prevents screen capture and recording of the kiosk WebView.",
            Prefs.BLOCK_SCREENSHOTS, false)
        bindSwitchRow(R.id.switch_immersive, "Hide status & nav bars",
            "Fullscreen immersive mode. Turn off if you want the status bar visible.",
            Prefs.IMMERSIVE, true)
    }

    private fun setupBehaviorSwitches() {
        bindSwitchRow(R.id.switch_screen_on, "Keep screen awake",
            "The screen never turns off while the kiosk is open.",
            Prefs.KEEP_SCREEN_ON, true)
        bindSwitchRow(R.id.switch_auto_reload, "Auto-reload on error",
            "If the page fails to load, retry the URL after a few seconds.",
            Prefs.AUTO_RELOAD_ON_ERROR, true)
        bindSwitchRow(R.id.switch_fab, "Floating exit button",
            "The draggable lock icon. Turn off only if your PIN backup gesture is set.",
            Prefs.SHOW_FLOATING_BUTTON, true)
    }

    private fun bindSwitchRow(
        includeId: Int, title: String, desc: String, prefKey: String, default: Boolean,
        onChange: ((Boolean) -> Unit)? = null
    ) {
        val row = findViewById<View>(includeId)
        row.findViewById<TextView>(R.id.switch_title).text = title
        row.findViewById<TextView>(R.id.switch_desc).text = desc
        val toggle = row.findViewById<MaterialSwitch>(R.id.switch_toggle)
        toggle.isChecked = prefs.getBoolean(prefKey, default)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefKey, isChecked).apply()
            onChange?.invoke(isChecked)
        }
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
    }

    // ============================================================
    // Lockdown + Launch
    // ============================================================
    private fun setupLockdown() {
        lockdownBtn = findViewById(R.id.lockdown_btn)
        val launchBtn = findViewById<MaterialButton>(R.id.launch_btn)

        lockdownBtn.setOnClickListener { toggleLockdown() }

        launchBtn.setOnClickListener {
            if (prefs.getString(Prefs.URL, "").isNullOrBlank() ||
                prefs.getString(Prefs.PIN, "").isNullOrBlank()) {
                Toast.makeText(this, "Save URL and PIN first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun refreshLockdownButton() {
        val on = prefs.getBoolean(Prefs.LOCKDOWN, false)
        lockdownBtn.text = if (on) "Turn lockdown OFF" else "Turn lockdown ON"
        lockdownBtn.setBackgroundColor(
            getColor(if (on) R.color.neon_pink else R.color.neon_purple)
        )
    }

    private fun toggleLockdown() {
        val currentlyOn = prefs.getBoolean(Prefs.LOCKDOWN, false)
        if (currentlyOn) {
            prefs.edit().putBoolean(Prefs.LOCKDOWN, false).apply()
            OverlayService.stop(this)
            Toast.makeText(this, "Lockdown disabled", Toast.LENGTH_SHORT).show()
            refreshLockdownButton()
        } else {
            val home = PermissionHelper.isDefaultHome(this)
            val acc = PermissionHelper.isAccessibilityEnabled(this)
            if (!home || !acc) {
                Toast.makeText(this, "Grant Home + Accessibility first", Toast.LENGTH_LONG).show()
                return
            }
            if (prefs.getString(Prefs.URL, "").isNullOrBlank() ||
                prefs.getString(Prefs.PIN, "").isNullOrBlank()) {
                Toast.makeText(this, "Save URL and PIN first", Toast.LENGTH_SHORT).show()
                return
            }
            prefs.edit()
                .putBoolean(Prefs.LOCKDOWN, true)
                .putBoolean(Prefs.EXITED, false)  // in case we were in Standby
                .apply()
            if (prefs.getBoolean(Prefs.BLOCK_NOTIFICATIONS, true) &&
                PermissionHelper.canDrawOverlays(this)) {
                OverlayService.start(this)
            }
            Toast.makeText(this, "Lockdown ON — launching kiosk", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // ============================================================
    // Utilities
    // ============================================================
    private fun setupUtilities() {
        val clearCacheBtn = findViewById<MaterialButton>(R.id.clear_cache_btn)
        val resetBtn = findViewById<MaterialButton>(R.id.reset_btn)

        clearCacheBtn.setOnClickListener {
            try {
                WebView(this).clearCache(true)
                deleteDatabase("webview.db")
                deleteDatabase("webviewCache.db")
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        resetBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset kiosk?")
                .setMessage("Wipes URL, PIN, lockdown state, and all block/behavior toggles. Android permissions granted to the app in phone settings are unaffected.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().clear().apply()
                    Prefs.defaults(prefs)
                    OverlayService.stop(this)
                    Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ============================================================
    // Full Exit — soft, non-destructive
    // ============================================================
    private fun setupFullExit() {
        findViewById<MaterialButton>(R.id.full_exit_btn).setOnClickListener { promptFullExit() }
    }

    private fun promptFullExit() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Fully exit Uno Kiosk?")
            .setMessage(
                "This puts the app into Standby mode:\n\n" +
                "• Lockdown OFF\n" +
                "• Overlay stopped\n" +
                "• Won't auto-launch on boot\n" +
                "• Won't come back when Home is pressed*\n\n" +
                "Your PIN, URL, all permissions and toggles are kept exactly as they are — nothing is removed.\n\n" +
                "Reactivate any time by opening Uno Kiosk from the app drawer.\n\n" +
                "*If Uno Kiosk is your default Home app, the next Home press will trigger the launcher picker. Choose your normal launcher.\n\n" +
                "Confirm with admin PIN:"
            )
            .setView(input)
            .setPositiveButton("Full Exit") { _, _ ->
                val entered = input.text.toString()
                val savedPin = prefs.getString(Prefs.PIN, "") ?: ""
                if (entered.isNotEmpty() && entered == savedPin) {
                    performFullExit()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performFullExit() {
        // Soft exit — no permissions or components are touched. All flags observe EXITED.
        prefs.edit()
            .putBoolean(Prefs.EXITED, true)
            .putBoolean(Prefs.LOCKDOWN, false)
            .apply()
        OverlayService.stop(this)

        // If we're currently the default Home app, help the user pick their normal
        // launcher — otherwise Home button would still route to Uno Kiosk.
        if (PermissionHelper.isDefaultHome(this)) {
            Toast.makeText(this,
                "Pick your regular launcher as default Home when prompted",
                Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
        } else {
            Toast.makeText(this, "Uno Kiosk is now in Standby", Toast.LENGTH_SHORT).show()
        }

        finishAffinity()
        window.decorView.postDelayed({ exitProcess(0) }, 400)
    }
}
