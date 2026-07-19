package com.uno.kiosk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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

        bindPermRow(
            row = permHome,
            iconRes = R.drawable.ic_home,
            title = "Home Launcher",
            desc = "Set Uno Kiosk as the default home app so the Home button can't leave it.",
            actionLabel = "Open settings"
        ) { PermissionHelper.openHomeSettings(this) }

        bindPermRow(
            row = permAcc,
            iconRes = R.drawable.ic_accessibility,
            title = "Accessibility Service",
            desc = "Blocks the app switcher and any other app the customer tries to launch.",
            actionLabel = "Open settings"
        ) { PermissionHelper.openAccessibilitySettings(this) }

        bindPermRow(
            row = permOverlay,
            iconRes = R.drawable.ic_layers,
            title = "Display over other apps",
            desc = "Covers the top edge so the notification shade can't be pulled down.",
            actionLabel = "Open settings"
        ) { PermissionHelper.openOverlaySettings(this) }

        bindPermRow(
            row = permBattery,
            iconRes = R.drawable.ic_battery,
            title = "Battery optimization",
            desc = "Stops Android from killing the app during long idle periods.",
            actionLabel = "Open settings"
        ) { PermissionHelper.openBatteryOptimizationRequest(this) }
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
        bindSwitchRow(
            R.id.switch_recents,
            "App Switcher (Recents)",
            "Kicks the customer back to the kiosk if they open the multitasking view.",
            Prefs.BLOCK_RECENTS, true
        )
        bindSwitchRow(
            R.id.switch_notifications,
            "Notification Shade",
            "Covers the status bar with an invisible overlay so it can't be pulled down.",
            Prefs.BLOCK_NOTIFICATIONS, true
        ) { on ->
            // If turning on and lockdown is active, start the overlay right away.
            if (prefs.getBoolean(Prefs.LOCKDOWN, false)) {
                if (on && PermissionHelper.canDrawOverlays(this)) OverlayService.start(this)
                else OverlayService.stop(this)
            }
        }
        bindSwitchRow(
            R.id.switch_volume,
            "Volume Buttons",
            "Ignores hardware volume up/down/mute presses while in the kiosk.",
            Prefs.BLOCK_VOLUME, false
        )
        bindSwitchRow(
            R.id.switch_screenshots,
            "Screenshots",
            "Prevents screen capture and recording of the kiosk WebView.",
            Prefs.BLOCK_SCREENSHOTS, false
        )
        bindSwitchRow(
            R.id.switch_immersive,
            "Hide status &amp; nav bars",
            "Fullscreen immersive mode. Turn off if you want the status bar visible.",
            Prefs.IMMERSIVE, true
        )
    }

    private fun setupBehaviorSwitches() {
        bindSwitchRow(
            R.id.switch_screen_on,
            "Keep screen awake",
            "The screen never turns off while the kiosk is open.",
            Prefs.KEEP_SCREEN_ON, true
        )
        bindSwitchRow(
            R.id.switch_auto_reload,
            "Auto-reload on error",
            "If the page fails to load, retry the URL after a few seconds.",
            Prefs.AUTO_RELOAD_ON_ERROR, true
        )
        bindSwitchRow(
            R.id.switch_fab,
            "Floating exit button",
            "The draggable lock icon. Turn off only if your PIN backup gesture is set.",
            Prefs.SHOW_FLOATING_BUTTON, true
        )
    }

    private fun bindSwitchRow(
        includeId: Int,
        title: String,
        desc: String,
        prefKey: String,
        default: Boolean,
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
}
