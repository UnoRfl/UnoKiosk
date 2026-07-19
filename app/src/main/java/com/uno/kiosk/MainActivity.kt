package com.uno.kiosk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var floatingExit: ImageView
    private var tapCount = 0
    private var lastTapTime = 0L

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        val url = prefs.getString(Prefs.URL, null)
        val pin = prefs.getString(Prefs.PIN, null)
        if (url.isNullOrBlank() || pin.isNullOrBlank()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        floatingExit = findViewById(R.id.floating_exit)
        val exitHotspot = findViewById<View>(R.id.exit_hotspot)

        applyImmersive()
        setupWebView(url)
        setupCornerHotspot(exitHotspot)
        setupFloatingButton()

        maybeStartLockdownAssist()
    }

    private fun setupWebView(url: String) {
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
    }

    private fun setupCornerHotspot(exitHotspot: View) {
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingButton() {
        val savedX = prefs.getFloat(Prefs.FAB_X, -1f)
        val savedY = prefs.getFloat(Prefs.FAB_Y, -1f)
        floatingExit.post {
            if (savedX >= 0f && savedY >= 0f) {
                floatingExit.x = clampX(savedX)
                floatingExit.y = clampY(savedY)
            }
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var downRawX = 0f
        var downRawY = 0f
        var offsetX = 0f
        var offsetY = 0f
        var isDragging = false

        floatingExit.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    offsetX = view.x - event.rawX
                    offsetY = view.y - event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        view.x = clampX(event.rawX + offsetX)
                        view.y = clampY(event.rawY + offsetY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val parent = view.parent as View
                        val midX = parent.width / 2f
                        val snappedX = if (view.x + view.width / 2f < midX) {
                            16f
                        } else {
                            parent.width - view.width - 16f
                        }
                        view.animate().x(snappedX).setDuration(150).start()
                        prefs.edit()
                            .putFloat(Prefs.FAB_X, snappedX)
                            .putFloat(Prefs.FAB_Y, view.y)
                            .apply()
                    } else {
                        view.performClick()
                        promptForPin()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun clampX(x: Float): Float {
        val parentW = (floatingExit.parent as? View)?.width ?: return x
        return x.coerceIn(0f, (parentW - floatingExit.width).toFloat().coerceAtLeast(0f))
    }

    private fun clampY(y: Float): Float {
        val parentH = (floatingExit.parent as? View)?.height ?: return y
        return y.coerceIn(0f, (parentH - floatingExit.height).toFloat().coerceAtLeast(0f))
    }

    private fun maybeStartLockdownAssist() {
        val lockdown = prefs.getBoolean(Prefs.LOCKDOWN, false)
        if (lockdown && PermissionHelper.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }

    private fun promptForPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Unlock Kiosk")
            .setMessage("Enter admin PIN")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = input.text.toString()
                val savedPin = prefs.getString(Prefs.PIN, "") ?: ""
                if (entered.isNotEmpty() && entered == savedPin) {
                    // Turn OFF lockdown so admin can freely navigate.
                    prefs.edit().putBoolean(Prefs.LOCKDOWN, false).apply()
                    OverlayService.stop(this)
                    startActivity(Intent(this, SettingsActivity::class.java))
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
        val currentUrl = prefs.getString(Prefs.URL, null)
        if (!currentUrl.isNullOrBlank() && ::webView.isInitialized && webView.url != currentUrl) {
            webView.loadUrl(currentUrl)
        }
        maybeStartLockdownAssist()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        }
        // else: swallow — stay in kiosk
    }
}
