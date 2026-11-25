package io.github.coolcoderme.haphone

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.coolcoderme.haphone.service.HubService
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var setup: View
    private val app get() = application as HaPhoneApp
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val loading = AtomicBoolean(false)
    private var loadAttempts = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { finishSetup() }

    private val readyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadHub()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        setup = findViewById(R.id.setup)
        webView.setBackgroundColor(Color.parseColor("#111111"))
        webView.setBackgroundResource(android.R.color.transparent)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        if (Build.VERSION.SDK_INT >= 21) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.setBackgroundColor(Color.parseColor("#111111"))
                injectAutoLogin()
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                scheduleReload()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request == null || request.isForMainFrame) scheduleReload()
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(JsBridge(), "HaPhone")

        findViewById<Button>(R.id.btn_continue).setOnClickListener { requestStartupPermissions() }
        findViewById<TextView>(R.id.setup_title).text = getString(R.string.setup_title)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){try{return window.HaPhoneUi&&window.HaPhoneUi.onBack&&window.HaPhoneUi.onBack()?'true':'false';}catch(e){return 'false';}})()"
                ) { result ->
                    val handled = result == "true" || result == "\"true\""
                    if (!handled) moveTaskToBack(true)
                }
            }
        })

        ContextCompat.registerReceiver(
            this,
            readyReceiver,
            IntentFilter(HubService.ACTION_READY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        HubService.start(this)

        if (!app.prefs.firstLaunchDone) {
            setup.visibility = View.VISIBLE
            webView.visibility = View.GONE
        } else {
            setup.visibility = View.GONE
            loadHub()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(readyReceiver) }
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun finishSetup() {
        promptBattery()
        app.prefs.firstLaunchDone = true
        setup.visibility = View.GONE
        webView.visibility = View.VISIBLE
        loadHub()
    }

    private fun promptBattery() {
        if (Build.VERSION.SDK_INT < 23) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun loadHub() {
        if (loading.getAndSet(true)) return
        val port = app.prefs.webPort
        ioExecutor.execute {
            repeat(24) {
                val connected = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                    }
                    true
                }.getOrDefault(false)
                if (connected) return@repeat
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    return@execute
                }
            }
            mainHandler.post {
                loading.set(false)
                webView.setBackgroundColor(Color.parseColor("#111111"))
                webView.loadUrl("http://127.0.0.1:$port/")
            }
        }
    }

    private fun scheduleReload() {
        if (loadAttempts >= 8) return
        loadAttempts += 1
        mainHandler.postDelayed({ loadHub() }, 500L * loadAttempts)
    }

    private fun injectAutoLogin() {
        val token = io.github.coolcoderme.haphone.api.HaCompat.ACCESS_TOKEN
        val port = app.prefs.webPort
        val script = """
            (function(){
              try {
                var tokens = {
                  hassUrl: 'http://127.0.0.1:$port',
                  clientId: 'http://127.0.0.1:$port/',
                  access_token: '$token',
                  refresh_token: 'local-refresh',
                  expires: Date.now() + 315360000000,
                  expires_in: 315360000,
                  token_type: 'Bearer'
                };
                localStorage.setItem('hassTokens', JSON.stringify(tokens));
              } catch (e) {}
            })();
        """.trimIndent()
        if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(script, null)
        else webView.loadUrl("javascript:$script")
    }

    inner class JsBridge {
        @android.webkit.JavascriptInterface
        fun scan() {
            HubService.start(this@MainActivity)
            startService(Intent(this@MainActivity, HubService::class.java).setAction(HubService.ACTION_SCAN))
        }

        @android.webkit.JavascriptInterface
        fun mqttReconnect() {
            HubService.start(this@MainActivity)
            startService(Intent(this@MainActivity, HubService::class.java).setAction(HubService.ACTION_MQTT_RECONNECT))
        }

        @android.webkit.JavascriptInterface
        fun battery() {
            runOnUiThread { promptBattery() }
        }

        @android.webkit.JavascriptInterface
        fun ssid(): String = io.github.coolcoderme.haphone.net.Lan.wifiSsid(this@MainActivity) ?: ""
    }
}
