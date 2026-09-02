package com.zcode.remote

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class ConnectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_connection_id"
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var url: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        val connectionId = intent.getStringExtra(EXTRA_ID)
        val conn = connectionId?.let { ConnectionsStore(this).get(it) }
        if (conn == null) {
            finish()
            return
        }
        url = conn.url

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = conn.name
        toolbar.setNavigationOnClickListener { finish() }
        setSupportActionBar(toolbar)

        findViewById<MaterialButton>(R.id.btn_refresh).setOnClickListener {
            webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // 保持当前连接画面：按返回只是退到后台，回来还在远程页；
                    // 想退出连接请点左上角返回箭头。
                    moveTaskToBack(true)
                }
            }
        })

        progress = findViewById(R.id.progress)
        webView = findViewById(R.id.web_view)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                return handleUrl(target)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUrl(url ?: return false)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    progress.visibility = View.GONE
                    Snackbar.make(webView, R.string.load_failed, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        webView.loadUrl(url)
    }

    private fun handleUrl(target: String): Boolean {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return false // WebView 内打开
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            true
        } catch (e: Exception) {
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.connect_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_open_browser -> {
            openInSystemBrowser()
            true
        }
        R.id.action_change_link -> {
            startActivity(Intent(this, AddLinkActivity::class.java))
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openInSystemBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Snackbar.make(webView, R.string.load_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}