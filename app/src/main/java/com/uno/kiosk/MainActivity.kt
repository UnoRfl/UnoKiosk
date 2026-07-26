package com.uno.kiosk

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var floatingExit: ImageView
    private var tapCount = 0
    private var lastTapTime = 0L
    private var errorReloadPending = false

    /** The URL we actually loaded. Used so SPA navigation isn't clobbered on resume. */
    private var loadedBaseUrl: String? = null

    // ---- File chooser state ----
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    /** Set when a chooser was requested but CAMERA permission had to be asked first. */
    private var pendingCameraOnlyRequest = false

    // ---- WebView getUserMedia permission state ----
    private var pendingWebPermissionRequest: PermissionRequest? = null

    private val chooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleChooserResult(result)
        }

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingCameraOnlyRequest) {
                pendingCameraOnlyRequest = false
                if (granted) {
                    launchCameraCapture()
                } else {
                    Toast.makeText(this, "Camera permission is required to take the photo", Toast.LENGTH_LONG).show()
                    cancelFileChooser()
                }
            }
            // getUserMedia path
            pendingWebPermissionRequest?.let { req ->
                pendingWebPermissionRequest = null
                if (granted) req.grant(req.resources) else req.deny()
            }
        }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        Prefs.defaults(prefs)

        // Standby: Full Exit was pressed. Never show the kiosk; go to Settings.
        if (prefs.getBoolean(Prefs.EXITED, false)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

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

    // ============================================================
    // WebView
    // ============================================================
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString UnoKiosk/7"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            // ---- <input type="file"> : the whole point of v7 ----
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                // Never leave a stale callback: WebView permanently disables the
                // input if a previous chooser never reported back.
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val acceptTypes = params?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
                val wantsImage = acceptTypes.any {
                    it.startsWith("image/") || it == "image/*"
                } || acceptTypes.isEmpty()
                val captureRequested =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && params?.isCaptureEnabled == true
                val allowMultiple =
                    params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE

                when {
                    // capture="environment" + image/* → the web app explicitly wants
                    // a live camera shot and blocks the gallery on purpose.
                    captureRequested && wantsImage -> requestCameraCapture()

                    // image/* with no capture attr → let the user pick camera or gallery.
                    wantsImage && acceptTypes.all { it.startsWith("image/") } ->
                        launchImageChooser(allowMultiple)

                    // Everything else (.csv/.xlsx/.xls imports) → document picker.
                    else -> launchDocumentPicker(acceptTypes, allowMultiple)
                }
                return true
            }

            // ---- getUserMedia (not used today, but future-proof) ----
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    val needsCamera = request.resources.any {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }
                    val hasCamera = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (needsCamera && !hasCamera) {
                        pendingWebPermissionRequest = request
                        PickerGuard.begin(60_000)
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        request.grant(request.resources)
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                // Same-origin kiosk content: allow silently.
                callback?.invoke(origin, true, false)
            }
        }

        // ---- Downloads (CSV/XLSX exports the web app may add later) ----
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(downloadUrl, userAgent, contentDisposition, mimeType)
        }

        loadedBaseUrl = url
        webView.loadUrl(url)
    }

    private fun handleLoadFailure() {
        if (!prefs.getBoolean(Prefs.AUTO_RELOAD_ON_ERROR, true)) return
        if (errorReloadPending) return
        errorReloadPending = true
        webView.postDelayed({
            errorReloadPending = false
            val url = prefs.getString(Prefs.URL, null) ?: return@postDelayed
            loadedBaseUrl = url
            webView.loadUrl(url)
        }, 4000)
    }

    // ============================================================
    // File chooser plumbing
    // ============================================================

    /** Maps HTML accept entries (".csv", "image/*") to concrete MIME types. */
    private fun acceptToMimeTypes(acceptTypes: List<String>): Array<String> {
        if (acceptTypes.isEmpty()) return arrayOf("*/*")
        val out = linkedSetOf<String>()
        for (raw in acceptTypes) {
            for (piece in raw.split(",")) {
                val t = piece.trim().lowercase()
                if (t.isEmpty()) continue
                when {
                    t == ".csv" -> {
                        out += "text/csv"
                        out += "text/comma-separated-values"
                        out += "application/csv"
                        out += "text/plain" // many pickers report CSV as text/plain
                    }
                    t == ".xlsx" ->
                        out += "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    t == ".xls" -> {
                        out += "application/vnd.ms-excel"
                        out += "application/excel"
                    }
                    t == ".pdf" -> out += "application/pdf"
                    t == ".txt" -> out += "text/plain"
                    t == ".json" -> out += "application/json"
                    t.startsWith(".") -> out += "application/octet-stream"
                    t.contains("/") -> out += t
                }
            }
        }
        return if (out.isEmpty()) arrayOf("*/*") else out.toTypedArray()
    }

    private fun requestCameraCapture() {
        val hasCamera = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCamera) {
            launchCameraCapture()
        } else {
            pendingCameraOnlyRequest = true
            PickerGuard.begin(60_000)
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        val intent = buildCameraIntent()
        if (intent == null) {
            Toast.makeText(this, "No camera app found on this device", Toast.LENGTH_LONG).show()
            cancelFileChooser()
            return
        }
        beginPickerSession()
        try {
            chooserLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            endPickerSession()
            Toast.makeText(this, "No camera app found on this device", Toast.LENGTH_LONG).show()
            cancelFileChooser()
        }
    }

    private fun buildCameraIntent(): Intent? {
        return try {
            val dir = File(cacheDir, "captures").apply { mkdirs() }
            val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", photoFile
            )
            pendingCameraUri = uri
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.takeIf { it.resolveActivity(packageManager) != null }
        } catch (_: Exception) {
            null
        }
    }

    private fun launchImageChooser(allowMultiple: Boolean) {
        val content = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            if (allowMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val hasCamera = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val cameraIntent = if (hasCamera) buildCameraIntent() else null

        val chooser = Intent.createChooser(content, "Select image").apply {
            if (cameraIntent != null) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
        }
        beginPickerSession()
        try {
            chooserLauncher.launch(chooser)
        } catch (_: ActivityNotFoundException) {
            endPickerSession()
            cancelFileChooser()
        }
    }

    private fun launchDocumentPicker(acceptTypes: List<String>, allowMultiple: Boolean) {
        pendingCameraUri = null
        val mimes = acceptToMimeTypes(acceptTypes)
        // "*/*" + EXTRA_MIME_TYPES is the most reliable combo across OEM pickers.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimes)
            if (allowMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        beginPickerSession()
        try {
            chooserLauncher.launch(Intent.createChooser(intent, "Select file"))
        } catch (_: ActivityNotFoundException) {
            // Fall back to GET_CONTENT on devices without a SAF document UI.
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, mimes)
                if (allowMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            try {
                chooserLauncher.launch(fallback)
            } catch (_: ActivityNotFoundException) {
                endPickerSession()
                Toast.makeText(this, "No file manager found on this device", Toast.LENGTH_LONG).show()
                cancelFileChooser()
            }
        }
    }

    private fun handleChooserResult(result: ActivityResult) {
        endPickerSession()
        val cb = filePathCallback
        filePathCallback = null

        if (cb == null) return

        if (result.resultCode != RESULT_OK) {
            cb.onReceiveValue(null)
            pendingCameraUri = null
            return
        }

        val data = result.data
        val uris: Array<Uri>? = when {
            // Camera: data is usually null, the photo is at pendingCameraUri.
            data == null || (data.data == null && data.clipData == null) -> {
                val camUri = pendingCameraUri
                if (camUri != null && cameraFileHasContent(camUri)) arrayOf(camUri) else null
            }
            data.clipData != null -> {
                val clip = data.clipData!!
                val list = ArrayList<Uri>(clip.itemCount)
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { list.add(it) }
                }
                if (list.isEmpty()) null else list.toTypedArray()
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }

        pendingCameraUri = null
        cb.onReceiveValue(uris)
    }

    private fun cameraFileHasContent(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun cancelFileChooser() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingCameraUri = null
    }

    /** Opens the "system picker is legitimately in front" window. */
    private fun beginPickerSession() {
        PickerGuard.begin()
        // The status-bar overlay would sit on top of the camera app's shutter UI.
        OverlayService.stop(this)
    }

    private fun endPickerSession() {
        PickerGuard.end()
        maybeStartLockdownAssist()
    }

    // ============================================================
    // Downloads
    // ============================================================
    private fun handleDownload(
        url: String, userAgent: String?, contentDisposition: String?, mimeType: String?
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(
                this,
                "This file is generated in-page and can't be saved from the kiosk.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let {
                    addRequestHeader("Cookie", it)
                }
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ============================================================
    // Exit affordances
    // ============================================================
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
        var downRawX = 0f; var downRawY = 0f
        var offsetX = 0f; var offsetY = 0f
        var isDragging = false

        floatingExit.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    offsetX = view.x - event.rawX; offsetY = view.y - event.rawY
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX; val dy = event.rawY - downRawY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) isDragging = true
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
                        val snappedX = if (view.x + view.width / 2f < midX) 24f
                                       else parent.width - view.width - 24f
                        view.animate().x(snappedX).setDuration(150).start()
                        prefs.edit().putFloat(Prefs.FAB_X, snappedX).putFloat(Prefs.FAB_Y, view.y).apply()
                    } else {
                        view.performClick(); promptForPin()
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
        if (PickerGuard.isActive) return
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
        if (prefs.getBoolean(Prefs.EXITED, false)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        applyScreenPrefs()
        applyImmersive()

        // Only reload when the CONFIGURED url actually changed. Reloading on
        // every resume would wipe in-progress SPA state (e.g. a captured photo
        // waiting to be submitted) every time the camera closes.
        val currentUrl = prefs.getString(Prefs.URL, null)
        if (!currentUrl.isNullOrBlank() && ::webView.isInitialized && currentUrl != loadedBaseUrl) {
            loadedBaseUrl = currentUrl
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
    }

    override fun onDestroy() {
        // Never leave the WebView waiting on a callback that will never arrive.
        cancelFileChooser()
        PickerGuard.end()
        super.onDestroy()
    }
}
