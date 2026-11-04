package ir.halgheyar.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var container: FrameLayout
    private lateinit var assetLoader: WebViewAssetLoader

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val HOME_URL = "https://$APP_HOST/assets/site/index.html"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        container = FrameLayout(this)
        webView = WebView(this)
        container.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(container)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        configureWebView()
        if (savedInstanceState == null) webView.loadUrl(HOME_URL)
        else webView.restoreState(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
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

    private fun showLoadError() {
        if (container.childCount > 1) return
        val message = TextView(this).apply {
            text = "بارگذاری صفحه با مشکل روبه‌رو شد. اتصال اینترنت را بررسی و دوباره تلاش کن.\n\nبرای تلاش مجدد لمس کن"
            textSize = 16f
            gravity = Gravity.CENTER
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
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
