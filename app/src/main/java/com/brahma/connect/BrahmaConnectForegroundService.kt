package com.brahma.connect

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
import androidx.core.app.NotificationCompat
import com.brahma.connect.commands.DeviceCommandHandler
import com.brahma.connect.core.AgentStateStore
import com.brahma.connect.core.ConnectionState
import com.brahma.connect.core.GatewayEndpoint
import com.brahma.connect.network.BrahmaWebSocketClient
import com.brahma.connect.pairing.PairingStorage
import com.brahma.connect.voice.BrahmaVoiceManager
import com.brahma.connect.voice.WakeWordListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BrahmaConnectForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var storage: PairingStorage
    private lateinit var client: BrahmaWebSocketClient
    private lateinit var wakeWordListener: WakeWordListener

    private var started = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        storage = PairingStorage(this)
        val commandHandler = DeviceCommandHandler(this)
        client = BrahmaWebSocketClient(this, storage, commandHandler)
        BrahmaConnectApplication.webSocketClient = client

        // Setup background wake-word listener for "Hey Brahma"
        wakeWordListener = WakeWordListener(this) {
            updateNotification("Hey Brahma Activated!", "Listening to your spoken command...")
            BrahmaVoiceManager.startListening { query ->
                if (query.isNotBlank()) {
                    client.sendChatMessage(query)
                }
            }
        }

        // Start listening to connection state
        scope.launch {
            AgentStateStore.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.CONNECTED -> updateNotification("Brahma Active", "Linked to Brahma PC • 'Hey Brahma' ready")
                    ConnectionState.CONNECTING -> updateNotification("Brahma Connecting", "Establishing link...")
                    ConnectionState.RECONNECTING -> updateNotification("Brahma Reconnecting", "Waiting for server...")
                    ConnectionState.DISCONNECTED -> updateNotification("Brahma Standby", "Disconnected • 'Hey Brahma' active")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val notification = buildNotification("Brahma Connect", "Service active • Voice standby")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Start continuous background wake word detector
            wakeWordListener.start()

            // Connect if credentials exist
            connectIfPossible()
        }
        return START_STICKY
    }

    private fun connectIfPossible() {
        val cred = storage.loadCredential()
        if (cred != null) {
            val endpoint = GatewayEndpoint(
                name = cred.deviceName,
                host = cred.gatewayHost,
                port = cred.gatewayPort,
                online = true
            )
            client.connect(endpoint, cred)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Brahma Connect Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps link alive and listens for 'Hey Brahma' wake word"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_brahma_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        wakeWordListener.stop()
        client.disconnect()
        scope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "brahma_connect_channel"
        const val NOTIFICATION_ID = 1001
    }
}
