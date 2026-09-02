package com.zcode.remote

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.snackbar.Snackbar

class ConnectActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var store: ConnectionsStore
    private var url: String = ""
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 浮动工具条（固定顶部中间，可收起）
    private lateinit var floatBar: LinearLayout
    private lateinit var miniFab: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)
        store = ConnectionsStore(this)
        InsetsHelper.apply(findViewById(R.id.root), handleIme = true)

        val connectionId = intent.getStringExtra(EXTRA_ID)
        val conn = connectionId?.let { store.get(it) }
        if (conn == null) {
            finish()
            return
        }
        url = conn.url
        store.saveLastConnection(conn.id)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // 保持当前连接画面：按返回只是退到后台，回来还在远程页；
                    // 想退出连接请点工具条上的返回按钮。
                    moveTaskToBack(true)
                }
            }
        })

        progress = findViewById(R.id.progress)
        webView = findViewById(R.id.web_view)
        floatBar = findViewById(R.id.float_bar)
        miniFab = findViewById(R.id.mini_fab)

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

        webView.webChromeClient = object : WebChromeClient() {
            // 远程页里的 <input type=file>（上传图片/文件）：接系统文件选择器（SAF），无需存储权限
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@ConnectActivity.filePathCallback?.onReceiveValue(null)
                this@ConnectActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                return try {
                    startActivityForResult(intent, REQ_FILE_CHOOSER)
                    true
                } catch (e: Exception) {
                    this@ConnectActivity.filePathCallback = null
                    Snackbar.make(webView, R.string.file_chooser_error, Snackbar.LENGTH_SHORT).show()
                    false
                }
            }
        }

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

        setupFloatBar()

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

    /** 主动退出连接：清除"最后连接"记忆并返回列表。 */
    private fun exitToMain() {
        store.saveLastConnection(null)
        finish()
    }

    // ---------- 顶部浮动工具条（固定居中，可收起） ----------

    private fun setupFloatBar() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { exitToMain() }
        findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btn_more).setOnClickListener { anchor -> showFloatMenu(anchor) }
        findViewById<ImageButton>(R.id.btn_collapse).setOnClickListener {
            floatBar.visibility = View.GONE
            miniFab.visibility = View.VISIBLE
            prefs().edit().putBoolean(KEY_COLLAPSED, true).apply()
        }
        findViewById<ImageButton>(R.id.btn_expand).setOnClickListener {
            miniFab.visibility = View.GONE
            floatBar.visibility = View.VISIBLE
            prefs().edit().putBoolean(KEY_COLLAPSED, false).apply()
        }

        if (prefs().getBoolean(KEY_COLLAPSED, false)) {
            floatBar.visibility = View.GONE
            miniFab.visibility = View.VISIBLE
        }
    }

    private fun showFloatMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.connect_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_open_browser -> {
                        openInSystemBrowser()
                        true
                    }
                    R.id.action_change_link -> {
                        store.saveLastConnection(null)
                        startActivity(Intent(this@ConnectActivity, AddLinkActivity::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun prefs() = getSharedPreferences("zcode_remote_ui", MODE_PRIVATE)

    private fun openInSystemBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Snackbar.make(webView, R.string.load_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    // ---------- 文件上传（网页 <input type=file>） ----------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE_CHOOSER) {
            val callback = filePathCallback
            filePathCallback = null
            if (callback != null) {
                val result = if (resultCode == RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) arrayOf(uri) else null
                } else null
                callback.onReceiveValue(result)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /** 切后台暂停渲染省电；返回时恢复。 */
    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "extra_connection_id"
        const val KEY_COLLAPSED = "collapsed"
        private const val REQ_FILE_CHOOSER = 701
    }
}