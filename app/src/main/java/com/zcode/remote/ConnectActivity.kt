package com.zcode.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class ConnectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_connection_id"

        /**
         * 注入到远程页的监控脚本：
         * 周期性扫描页面文本，发现「任务完成」或「需要用户确认」的状态变化时，
         * 通过 ZCodeRemoteBridge.postEvent 上报给 Android 层（去重、仅在状态变化瞬间触发一次）。
         * 页面是第三方官方网页，没有公开任务 API，因此用内容检测实现提醒。
         */
        private const val MONITOR_JS = "(function(){" +
            "if(window.__zcMon){return;}window.__zcMon=1;" +
            "var doneWords=['已完成','任务完成','完成','成功','done','completed','success','succeeded','finished'];" +
            "var askWords=['需要你确认','等待确认','请确认','确认操作','需要授权','请批准','请求确认','approve','pending approval','action required'];" +
            "var last={task_done:false,action_required:false};var firstRun=true;" +
            "function fire(kind,word){try{window.ZCodeRemoteBridge.postEvent(kind,String(word).slice(0,120));}catch(e){}}" +
            "function scan(){try{" +
            "var t=(document.body?document.body.innerText:'')||'';var parts=[];" +
            "var nodes=document.querySelectorAll('button,[role=button],input[type=button],[role=alert],[aria-live]');" +
            "for(var i=0;i<nodes.length;i++){var x=(nodes[i].innerText||nodes[i].value||'').trim();if(x)parts.push(x);}" +
            "var all=t+' '+parts.join(' ');" +
            "function check(words,kind){for(var i=0;i<words.length;i++){if(all.indexOf(words[i])>-1){if(!last[kind]){last[kind]=true;if(!firstRun)fire(kind,words[i]);}return;}}last[kind]=false;}" +
            "check(doneWords,'task_done');check(askWords,'action_required');firstRun=false;" +
            "}catch(e){}" +
            "}" +
            "setInterval(scan,5000);setTimeout(scan,2500);" +
            "})();"
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var url: String = ""
    private var backgrounded = false
    private var lastEventKey = ""
    private var lastEventTime = 0L

    private inner class ZCodeBridge {
        @JavascriptInterface
        fun postEvent(kind: String, text: String) {
            runOnUiThread { handleRemoteEvent(kind, text) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

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
        webView.addJavascriptInterface(ZCodeBridge(), "ZCodeRemoteBridge")

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
                injectMonitor()
                debugSelfTest()
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

    /** 页面加载完成后注入监控脚本（__zcMon 守卫保证只注入一次）。 */
    private fun injectMonitor() {
        webView.evaluateJavascript(MONITOR_JS, null)
    }

    /** 仅 debug 构建：页面加载 12s 后模拟一次“任务完成”事件，用于验证通知链路。 */
    private fun debugSelfTest() {
        if (!BuildConfig.DEBUG) return
        webView.postDelayed({
            webView.evaluateJavascript(
                "window.ZCodeRemoteBridge&&window.ZCodeRemoteBridge.postEvent('task_done','自测：任务已完成')",
                null
            )
        }, 12000)
    }

    private fun handleRemoteEvent(kind: String, text: String) {
        val now = System.currentTimeMillis()
        val key = "$kind|${text.take(80)}"
        if (key == lastEventKey && now - lastEventTime < 30_000L) return
        lastEventKey = key
        lastEventTime = now

        val title = when (kind) {
            "task_done" -> getString(R.string.notif_task_done)
            "action_required" -> getString(R.string.notif_action_required)
            else -> return
        }
        val message = text.ifBlank { title }
        if (backgrounded) {
            NotificationHelper.notifyRemoteEvent(this, title, message)
        } else {
            Snackbar.make(webView, "$title：$message", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        backgrounded = true
        webView.onPause() // 暂停渲染，但不停 JS 计时器，保证后台仍能监控
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        backgrounded = false
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