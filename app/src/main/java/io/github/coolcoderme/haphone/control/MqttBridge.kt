package io.github.coolcoderme.haphone.control

import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Shared MQTT connection used by [io.github.coolcoderme.haphone.service.HubService]
 * and device refresh/execute. Reconnects when Settings save a new broker.
 */
object MqttBridge {
    private const val TAG = "MqttBridge"
    private val lock = Any()
    private val payloads = ConcurrentHashMap<String, String>()
    @Volatile private var client: MqttClient? = null
    @Volatile var onMessage: ((topic: String, payload: String) -> Unit)? = null

    fun isConnected(): Boolean = client?.isConnected == true

    fun lastPayload(topic: String): String? =
        if (topic.isBlank()) null else payloads[topic]

    fun remember(topic: String, payload: String) {
        if (topic.isNotBlank()) payloads[topic] = payload
    }

    fun publish(topic: String, payload: String): Boolean {
        val c = client
        if (c == null || !c.isConnected || topic.isBlank()) return false
        return runCatching {
            c.publish(topic, payload.toByteArray(Charsets.UTF_8), 0, false)
            remember(topic, payload)
            true
        }.onFailure {
            Log.w(TAG, "publish $topic", it)
        }.getOrDefault(false)
    }

    fun subscribe(topic: String) {
        if (topic.isBlank()) return
        runCatching { client?.takeIf { it.isConnected }?.subscribe(topic, 0) }
            .onFailure { Log.w(TAG, "subscribe $topic", it) }
    }

    fun reconnect(broker: String, port: Int, user: String, password: String, topics: Collection<String>) {
        synchronized(lock) {
            runCatching { client?.disconnect() }
            runCatching { client?.close() }
            client = null
            if (broker.isBlank()) return
            runCatching {
                val c = MqttClient(
                    "tcp://$broker:$port",
                    "haphone-" + UUID.randomUUID().toString(),
                    MemoryPersistence()
                )
                c.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        topics.forEach { subscribe(it) }
                    }
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "connection lost", cause)
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val t = topic ?: return
                        val body = message?.payload?.toString(Charsets.UTF_8) ?: return
                        remember(t, body)
                        onMessage?.invoke(t, body)
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 8
                    if (user.isNotBlank()) {
                        userName = user
                        this.password = password.toCharArray()
                    }
                }
                c.connect(opts)
                client = c
                topics.forEach { subscribe(it) }
            }.onFailure {
                Log.w(TAG, "connect $broker:$port", it)
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            runCatching { client?.disconnect() }
            runCatching { client?.close() }
            client = null
        }
    }
}
