package com.uno.kiosk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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

    // Lockdown control
    private lateinit var lockdownBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        Prefs.defaults(prefs)

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
    // Permission rows
    // ============================================================
    private fun setupPermissionRows() {
        permHome = findViewById(R.id.perm_home)
        permAcc = findViewById(R.id.perm_accessibility)
        permOverlay = findViewById(R.id.perm_overlay)
        permBattery = findViewById(R.id.perm_battery)

        bindPermRow(permHome, R.drawable.ic_home, "Home Launcher",
            "Set Uno Kiosk as the default home app so the Home button can't leave it.",
            "Open settings") { PermissionHelper.openHomeSettings(this) }

        bindPermRow(permAcc, R.drawable.ic_accessibility, "Accessibility Service",
            "Blocks the app switcher and any other app the customer tries to launch.",
            "Open settings") { PermissionHelper.openAccessibilitySettings(this) }

        bindPermRow(permOverlay, R.drawable.ic_layers, "Display over other apps",
            "Covers the top edge so the notification shade can't be pulled down.",
            "Open settings") { PermissionHelper.openOverlaySettings(this) }

        bindPermRow(permBattery, R.drawable.ic_battery, "Battery optimization",
            "Stops Android from killing the app during long idle periods.",
            "Open settings") { PermissionHelper.openBatteryOptimizationRequest(this) }
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
    // Block switches
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
            // Re-enable manifest components in case a previous Full Exit disabled them.
            AppComponents.enableKioskComponents(this)
            prefs.edit().putBoolean(Prefs.LOCKDOWN, true).apply()
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
    // Full Exit — the "big red button"
    // ============================================================
    private fun setupFullExit() {
        val fullExitBtn = findViewById<MaterialButton>(R.id.full_exit_btn)
        fullExitBtn.setOnClickListener { promptFullExit() }
    }

    private fun promptFullExit() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Fully exit Uno Kiosk?")
            .setMessage(
                "This will:\n\n" +
                "• Turn lockdown OFF\n" +
                "• Stop the notification overlay\n" +
                "• Remove Uno Kiosk as the Home launcher\n" +
                "• Disable auto-launch on boot\n" +
                "• Close the app\n\n" +
                "Home button will go to your normal launcher. To bring the kiosk back, open Uno Kiosk from the app drawer and turn Lockdown ON.\n\n" +
                "Confirm with your admin PIN:"
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
        // 1. Turn off lockdown so the accessibility service stops bouncing.
        prefs.edit().putBoolean(Prefs.LOCKDOWN, false).apply()
        // 2. Stop the status-bar overlay.
        OverlayService.stop(this)
        // 3. Disable the Home alias and boot receiver so the phone forgets us.
        AppComponents.disableKioskComponents(this)
        // 4. Close every activity in this task.
        finishAffinity()
        // 5. Kill the process — belt and suspenders.
        Toast.makeText(this, "Uno Kiosk fully exited", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ exitProcess(0) }, 300)
    }
}
