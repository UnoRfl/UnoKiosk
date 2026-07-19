package com.uno.kiosk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
    private var errorReloadPending = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        Prefs.defaults(prefs)

        val url = prefs.getString(Prefs.URL, null)
        val pin = prefs.getString(Prefs.PIN, null)
        if (url.isNullOrBlank() || pin.isNullOrBlank()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        applyScreenPrefs()

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

    private fun applyScreenPrefs() {
        if (prefs.getBoolean(Prefs.KEEP_SCREEN_ON, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (prefs.getBoolean(Prefs.BLOCK_SCREENSHOTS, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
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
            userAgentString = userAgentString + " UnoKiosk/4"
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                errorReloadPending = false
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request?.isForMainFrame == true) {
                    handleLoadFailure()
                }
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?, errorCode: Int, description: String?, failingUrl: String?
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) handleLoadFailure()
            }

            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) {
                // Restaurant self-hosted sites sometimes have flaky certs. Proceed by default,
                // matching cleartext-traffic behavior.
                handler?.proceed()
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(url)
    }

    private fun handleLoadFailure() {
        if (!prefs.getBoolean(Prefs.AUTO_RELOAD_ON_ERROR, true)) return
        if (errorReloadPending) return
        errorReloadPending = true
        webView.postDelayed({
            errorReloadPending = false
            val url = prefs.getString(Prefs.URL, null) ?: return@postDelayed
            webView.loadUrl(url)
        }, 4000)
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
        floatingExit.visibility =
            if (prefs.getBoolean(Prefs.SHOW_FLOATING_BUTTON, true)) View.VISIBLE else View.GONE

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
                            24f
                        } else {
                            parent.width - view.width - 24f
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
        val overlayOn = prefs.getBoolean(Prefs.BLOCK_NOTIFICATIONS, true)
        if (lockdown && overlayOn && PermissionHelper.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else {
            OverlayService.stop(this)
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
        val immersive = prefs.getBoolean(Prefs.IMMERSIVE, true)
        window.decorView.systemUiVisibility = if (immersive) {
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        applyScreenPrefs()
        applyImmersive()
        val currentUrl = prefs.getString(Prefs.URL, null)
        if (!currentUrl.isNullOrBlank() && ::webView.isInitialized && webView.url != currentUrl) {
            webView.loadUrl(currentUrl)
        }
        if (::floatingExit.isInitialized) {
            floatingExit.visibility =
                if (prefs.getBoolean(Prefs.SHOW_FLOATING_BUTTON, true)) View.VISIBLE else View.GONE
        }
        maybeStartLockdownAssist()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (prefs.getBoolean(Prefs.BLOCK_VOLUME, false)) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        }
        // else: swallow — stay in kiosk
    }
}
