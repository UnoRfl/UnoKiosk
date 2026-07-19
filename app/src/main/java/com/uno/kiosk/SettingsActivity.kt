package com.uno.kiosk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var homeStatus: TextView
    private lateinit var accStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var lockdownBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        val urlField = findViewById<EditText>(R.id.url_field)
        val pinField = findViewById<EditText>(R.id.pin_field)
        val saveBtn = findViewById<Button>(R.id.save_btn)
        val launchBtn = findViewById<Button>(R.id.launch_btn)
        val clearCacheBtn = findViewById<Button>(R.id.clear_cache_btn)
        val resetBtn = findViewById<Button>(R.id.reset_btn)

        homeStatus = findViewById(R.id.home_status)
        accStatus = findViewById(R.id.accessibility_status)
        overlayStatus = findViewById(R.id.overlay_status)
        batteryStatus = findViewById(R.id.battery_status)
        lockdownBtn = findViewById(R.id.lockdown_btn)

        val grantHomeBtn = findViewById<Button>(R.id.grant_home_btn)
        val grantAccBtn = findViewById<Button>(R.id.grant_accessibility_btn)
        val grantOverlayBtn = findViewById<Button>(R.id.grant_overlay_btn)
        val grantBatteryBtn = findViewById<Button>(R.id.grant_battery_btn)

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

        launchBtn.setOnClickListener {
            if (prefs.getString(Prefs.URL, "").isNullOrBlank() ||
                prefs.getString(Prefs.PIN, "").isNullOrBlank()) {
                Toast.makeText(this, "Save URL and PIN first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        clearCacheBtn.setOnClickListener {
            try {
                WebView(this).clearCache(true)
                deleteDatabase("webview.db")
                deleteDatabase("webviewCache.db")
                Toast.makeText(this, "WebView cache cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        resetBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset kiosk?")
                .setMessage("Wipes URL, PIN, and lockdown state. Permissions granted to the app in phone settings are unaffected.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().clear().apply()
                    OverlayService.stop(this)
                    Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show()
                    urlField.setText(""); pinField.setText("")
                    refreshStatuses()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        grantHomeBtn.setOnClickListener { PermissionHelper.openHomeSettings(this) }
        grantAccBtn.setOnClickListener { PermissionHelper.openAccessibilitySettings(this) }
        grantOverlayBtn.setOnClickListener { PermissionHelper.openOverlaySettings(this) }
        grantBatteryBtn.setOnClickListener { PermissionHelper.openBatteryOptimizationRequest(this) }

        lockdownBtn.setOnClickListener { toggleLockdown() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun refreshStatuses() {
        val home = PermissionHelper.isDefaultHome(this)
        val acc = PermissionHelper.isAccessibilityEnabled(this)
        val overlay = PermissionHelper.canDrawOverlays(this)
        val battery = PermissionHelper.isBatteryOptimizationIgnored(this)

        homeStatus.text = mark("Set as Home Launcher", home)
        accStatus.text = mark("Accessibility Service", acc)
        overlayStatus.text = mark("Display over other apps", overlay)
        batteryStatus.text = mark("Ignore battery optimization", battery)

        val lockdown = prefs.getBoolean(Prefs.LOCKDOWN, false)
        lockdownBtn.text = if (lockdown) "Kiosk Lockdown: ON — tap to turn OFF"
                          else "Kiosk Lockdown: OFF — tap to turn ON"
        lockdownBtn.isEnabled = home && acc // overlay + battery are recommended, not required
    }

    private fun mark(label: String, ok: Boolean): String {
        return (if (ok) "✓  " else "✗  ") + label
    }

    private fun toggleLockdown() {
        val currentlyOn = prefs.getBoolean(Prefs.LOCKDOWN, false)
        if (currentlyOn) {
            prefs.edit().putBoolean(Prefs.LOCKDOWN, false).apply()
            OverlayService.stop(this)
            Toast.makeText(this, "Lockdown disabled", Toast.LENGTH_SHORT).show()
            refreshStatuses()
        } else {
            val home = PermissionHelper.isDefaultHome(this)
            val acc = PermissionHelper.isAccessibilityEnabled(this)
            if (!home || !acc) {
                Toast.makeText(this, "Grant Home + Accessibility first", Toast.LENGTH_LONG).show()
                return
            }
            prefs.edit().putBoolean(Prefs.LOCKDOWN, true).apply()
            if (PermissionHelper.canDrawOverlays(this)) OverlayService.start(this)
            Toast.makeText(this, "Lockdown enabled. Launching kiosk…", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
