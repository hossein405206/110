package ir.halgheyar.app

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Intent
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.graphics.Color
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var container: FrameLayout
    private lateinit var offlineBanner: TextView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var updateDownloadId: Long = -1L
    private var updateReceiverRegistered = false
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != updateDownloadId) return
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = manager.query(DownloadManager.Query().setFilterById(id))
            val successful = cursor?.use {
                it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
            } ?: false
            if (successful) openDownloadedInstaller(manager.getUriForDownloadedFile(id))
            else Toast.makeText(this@MainActivity, "دریافت نسخه جدید ناموفق بود؛ دوباره تلاش کن.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val HOME_URL = "https://$APP_HOST/assets/site/index.html"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        container = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = WebView(this)
        container.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        offlineBanner = TextView(this).apply {
            text = "اتصال اینترنت برقرار نیست؛ بعضی امکانات در دسترس نخواهند بود"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(139, 46, 62))
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
            visibility = View.GONE
            elevation = 12f
            contentDescription = "وضعیت اتصال اینترنت"
        }
        container.addView(offlineBanner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        setContentView(container)
        ViewCompat.requestApplyInsets(container)
        WindowInsetsControllerCompat(window, container).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        configureWebView()
        webView.addJavascriptInterface(HalqehBridge(), "HalqehNative")
        setupNetworkMonitoring()
        val updateFilter = android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, updateFilter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(updateReceiver, updateFilter)
        updateReceiverRegistered = true
        if (savedInstanceState == null) webView.loadUrl(HOME_URL)
        else webView.restoreState(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }


    private inner class HalqehBridge {
        @JavascriptInterface
        fun getAppVersion(): String {
            return try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
            } catch (_: Exception) { "1.0.0" }
        }

        @JavascriptInterface
        fun downloadAndInstall(url: String) {
            runOnUiThread {
                val uri = try { Uri.parse(url) } catch (_: Exception) { null }
                if (uri == null || uri.scheme != "https" || uri.host != "github.com" ||
                    !(uri.path ?: "").contains("/releases/download/") ||
                    !(uri.path ?: "").endsWith(".apk", ignoreCase = true)) {
                    Toast.makeText(this@MainActivity, "پیوند دریافت نسخه معتبر نیست.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(this@MainActivity, "برای نصب به‌روزرسانی، اجازه نصب برنامه از این منبع را فعال کن.", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                    return@runOnUiThread
                }
                try {
                    val request = DownloadManager.Request(uri)
                        .setTitle("به‌روزرسانی حلقه‌یار")
                        .setDescription("دریافت نسخه جدید برنامه")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "halqeh-yar-update.apk")
                    updateDownloadId = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this@MainActivity, "دریافت نسخه جدید آغاز شد؛ پس از پایان، پنجره نصب باز می‌شود.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "شروع دریافت نسخه جدید ممکن نشد.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text.take(4000))
                }
                startActivity(Intent.createChooser(send, "اشتراک‌گذاری حلقه‌یار"))
            }
        }

        @JavascriptInterface
        fun setSystemBarsDark(isDark: Boolean) {
            runOnUiThread {
                WindowInsetsControllerCompat(window, container).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }

        @JavascriptInterface
        fun showLocalNotification(title: String, body: String) {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 410)
                    return@runOnUiThread
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel("halqeh_updates", "اعلان‌های حلقه‌یار", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
                val notification = NotificationCompat.Builder(this@MainActivity, "halqeh_updates")
                    .setSmallIcon(R.drawable.ic_halqeh_yar)
                    .setContentTitle(title.take(80))
                    .setContentText(body.take(180))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(500)))
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(this@MainActivity).notify(
                    (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification
                )
            }
        }
    }

    private fun openDownloadedInstaller(uri: Uri?) {
        if (uri == null) {
            Toast.makeText(this, "فایل به‌روزرسانی پیدا نشد.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newUri(contentResolver, "به‌روزرسانی حلقه‌یار", uri)
            }
            startActivity(installIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "باز کردن نصب‌کننده اندروید ممکن نشد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == APP_HOST) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: ActivityNotFoundException) {
                    true
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) showLoadError()
            }
        }
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { updateNetworkStatus() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { updateNetworkStatus() }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                runOnUiThread { updateNetworkStatus() }
            }
        }
        networkCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)
        updateNetworkStatus()

        offlineBanner.setOnClickListener {
            if (hasValidatedInternet()) {
                offlineBanner.visibility = View.GONE
                webView.reload()
            }
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateNetworkStatus() {
        val offline = !hasValidatedInternet()
        offlineBanner.visibility = if (offline) View.VISIBLE else View.GONE
        offlineBanner.isClickable = !offline
        offlineBanner.alpha = if (offline) 1f else 0f
    }

    private fun showLoadError() {
        if (container.findViewWithTag<View>("load-error") != null) return
        val message = TextView(this).apply {
            tag = "load-error"
            text = "بارگذاری صفحه با مشکل روبه‌رو شد. اتصال اینترنت را بررسی و دوباره تلاش کن.\n\nبرای تلاش مجدد لمس کن"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(250, 244, 230))
            setBackgroundColor(Color.rgb(16, 26, 44))
            setPadding(40, 40, 40, 40)
            setOnClickListener {
                container.removeView(this)
                webView.loadUrl(HOME_URL)
            }
        }
        container.addView(message, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        message.bringToFront()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        if (updateReceiverRegistered) {
            try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
            updateReceiverRegistered = false
        }
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
