package com.uno.kiosk

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private var tapCount = 0
    private var lastTapTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        // First-run check: no URL or no PIN saved → go to Settings.
        val url = prefs.getString(Prefs.URL, null)
        val pin = prefs.getString(Prefs.PIN, null)
        if (url.isNullOrBlank() || pin.isNullOrBlank()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // Keep screen on (kiosk device).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        val exitHotspot = findViewById<View>(R.id.exit_hotspot)

        applyImmersive()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(url)

        // Hidden exit: 5 taps within 3 seconds on the top-left corner.
        exitHotspot.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime > 3000) tapCount = 0
            tapCount++
            lastTapTime = now
            if (tapCount >= 5) {
                tapCount = 0
                promptForPin()
            }
        }

        // Kiosk lock — only works if the app is Device Owner (set via ADB).
        // If not, it silently falls back to a "soft kiosk" (fullscreen + HOME intent).
        tryStartLockTask()
    }

    private fun tryStartLockTask() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(packageName)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    dpm.setLockTaskPackages(
                        ComponentName(this, AdminReceiver::class.java),
                        arrayOf(packageName)
                    )
                }
                startLockTask()
            }
        } catch (_: Exception) {
            // ignore — soft kiosk mode
        }
    }

    private fun promptForPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Unlock Kiosk")
            .setMessage("Enter admin PIN to open settings")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = input.text.toString()
                val savedPin = prefs.getString(Prefs.PIN, "") ?: ""
                if (entered.isNotEmpty() && entered == savedPin) {
                    try { stopLockTask() } catch (_: Exception) {}
                    startActivity(Intent(this, SettingsActivity::class.java))
                    // Do not finish() — user comes back here after settings.
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        // Reload URL if it changed in settings.
        val currentUrl = prefs.getString(Prefs.URL, null)
        if (!currentUrl.isNullOrBlank() && ::webView.isInitialized && webView.url != currentUrl) {
            webView.loadUrl(currentUrl)
        }
        tryStartLockTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        }
        // else: swallow — stay in kiosk
    }
}

object Prefs {
    const val NAME = "uno_kiosk_prefs"
    const val URL = "url"
    const val PIN = "pin"
}
