package com.uno.kiosk

import android.app.admin.DevicePolicyManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        val urlField = findViewById<EditText>(R.id.url_field)
        val pinField = findViewById<EditText>(R.id.pin_field)
        val statusText = findViewById<TextView>(R.id.status_text)
        val saveBtn = findViewById<Button>(R.id.save_btn)
        val launchBtn = findViewById<Button>(R.id.launch_btn)
        val clearCacheBtn = findViewById<Button>(R.id.clear_cache_btn)
        val resetBtn = findViewById<Button>(R.id.reset_btn)

        urlField.setText(prefs.getString(Prefs.URL, ""))
        pinField.setText(prefs.getString(Prefs.PIN, ""))

        statusText.text = buildStatus()

        saveBtn.setOnClickListener {
            val url = urlField.text.toString().trim()
            val pin = pinField.text.toString().trim()

            if (url.isBlank()) {
                Toast.makeText(this, "URL required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val normalized = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url

            if (pin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString(Prefs.URL, normalized)
                .putString(Prefs.PIN, pin)
                .apply()

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            statusText.text = buildStatus()
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
                .setMessage("This wipes the saved URL and PIN. You'll set them again next launch.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().clear().apply()
                    Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show()
                    statusText.text = buildStatus()
                    urlField.setText("")
                    pinField.setText("")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun buildStatus(): String {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isOwner = try { dpm.isDeviceOwnerApp(packageName) } catch (_: Exception) { false }
        val hasUrl = !prefs.getString(Prefs.URL, "").isNullOrBlank()
        val hasPin = !prefs.getString(Prefs.PIN, "").isNullOrBlank()

        return buildString {
            appendLine("Device Owner: ${if (isOwner) "YES (hard kiosk)" else "NO (soft kiosk)"}")
            appendLine("URL set: ${if (hasUrl) "yes" else "no"}")
            appendLine("PIN set: ${if (hasPin) "yes" else "no"}")
            appendLine()
            appendLine("To enable HARD kiosk (locks the phone into this app):")
            appendLine("  adb shell dpm set-device-owner com.uno.kiosk/.AdminReceiver")
            appendLine()
            appendLine("To remove it:")
            appendLine("  adb shell dpm remove-active-admin com.uno.kiosk/.AdminReceiver")
            appendLine()
            appendLine("If you forget the PIN, factory-reset just this app's data:")
            appendLine("  adb shell pm clear com.uno.kiosk")
        }
    }
}
