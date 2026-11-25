package io.github.coolcoderme.haphone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.coolcoderme.haphone.HaPhoneApp
import io.github.coolcoderme.haphone.MainActivity
import io.github.coolcoderme.haphone.R
import io.github.coolcoderme.haphone.automation.AutomationEngine
import io.github.coolcoderme.haphone.discovery.DiscoveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class HubService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var server: HaWebServer? = null
    private var discovery: DiscoveryCoordinator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mqtt: MqttClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        val app = application as HaPhoneApp
        val repo = app.repo
        scope.launch { repo.ensureDefaults() }
        discovery = DiscoveryCoordinator(this, repo, scope).also { it.start() }
        AutomationEngine(repo, scope).start()
        if (repo.prefs.webDashboard) {
            val port = repo.prefs.webPort
            server = HaWebServer(this, repo, scope, port) { discovery?.scanNow() }
            runCatching { server?.start(60_000, false) }
        }
        scope.launch {
            repo.events.collect { event -> server?.broadcastEvent(event) }
        }
        scope.launch {
            while (isActive) {
                runCatching { repo.refreshAll() }
                delay((repo.prefs.pollSeconds.coerceAtLeast(5)) * 1000L)
            }
        }
        connectMqtt(app)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haphone:hub").apply {
            setReferenceCounted(false)
            acquire()
        }
        sendBroadcast(Intent(ACTION_READY).setPackage(packageName))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SCAN) discovery?.scanNow()
        return START_STICKY
    }

    override fun onDestroy() {
        discovery?.stop()
        server?.stop()
        runCatching { mqtt?.disconnect() }
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun connectMqtt(app: HaPhoneApp) {
        val broker = app.prefs.mqttBroker
        if (broker.isBlank()) return
        scope.launch {
            runCatching {
                val client = MqttClient(
                    "tcp://$broker:${app.prefs.mqttPort}",
                    "haphone-" + java.util.UUID.randomUUID().toString(),
                    MemoryPersistence()
                )
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 8
                    if (app.prefs.mqttUser.isNotBlank()) {
                        userName = app.prefs.mqttUser
                        password = app.prefs.mqttPassword.toCharArray()
                    }
                }
                client.connect(opts)
                mqtt = client
            }
        }
    }

    private fun startInForeground() {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hub)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(42, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Hub", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL = "hub"
        const val ACTION_READY = "io.github.coolcoderme.haphone.HUB_READY"
        const val ACTION_SCAN = "io.github.coolcoderme.haphone.SCAN"

        fun start(context: Context) {
            val intent = Intent(context, HubService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
