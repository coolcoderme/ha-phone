package io.github.coolcoderme.haphone

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.coolcoderme.haphone.service.HubService

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var setup: View
    private val app get() = application as HaPhoneApp

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
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        if (Build.VERSION.SDK_INT >= 21) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectAutoLogin()
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(JsBridge(), "HaPhone")

        findViewById<Button>(R.id.btn_continue).setOnClickListener { requestStartupPermissions() }
        findViewById<TextView>(R.id.setup_title).text = getString(R.string.setup_title)

        HubService.start(this)
        ContextCompat.registerReceiver(
            this,
            readyReceiver,
            IntentFilter(HubService.ACTION_READY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
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
        val port = app.prefs.webPort
        webView.loadUrl("http://127.0.0.1:$port/")
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
        fun battery() {
            runOnUiThread { promptBattery() }
        }

        @android.webkit.JavascriptInterface
        fun ssid(): String = io.github.coolcoderme.haphone.net.Lan.wifiSsid(this@MainActivity) ?: ""
    }
}
