package com.brahma.connect.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.brahma.connect.commands.DeviceCommandHandler
import com.brahma.connect.core.*
import com.brahma.connect.pairing.PairingStorage
import com.brahma.connect.voice.BrahmaVoiceManager
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

class BrahmaWebSocketClient(
    private val context: Context,
    private val storage: PairingStorage,
    private val commandHandler: DeviceCommandHandler
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var currentEndpoint: GatewayEndpoint? = null
    private var currentOffer: PairingOffer? = null
    private var currentCredential: DeviceCredential? = null
    private var manualDisconnect: Boolean = false
    private var reconnectAttempt: Int = 0
    private var lastConnectUptime: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(endpoint: GatewayEndpoint, credential: DeviceCredential? = null, offer: PairingOffer? = null) {
        manualDisconnect = false
        currentEndpoint = endpoint
        currentCredential = credential
        currentOffer = offer
        lastConnectUptime = SystemClock.elapsedRealtime()

        AgentStateStore.setGateway(endpoint)
        AgentStateStore.setConnectionState(ConnectionState.CONNECTING)
        AgentStateStore.setStatus("Connecting to ${endpoint.name}...")

        val url = "ws://${endpoint.host}:${endpoint.port}/ws"
        val request = Request.Builder().url(url).build()

        socket?.close(1000, "Reconnecting")
        socket = client.newWebSocket(request, BrahmaSocketListener())
    }

    fun disconnect() {
        manualDisconnect = true
        socket?.close(1000, "Disconnected by user")
        socket = null
        AgentStateStore.setConnectionState(ConnectionState.DISCONNECTED)
        AgentStateStore.setStatus("Disconnected")
    }

    private fun reconnectLater() {
        if (manualDisconnect || currentEndpoint == null) return
        AgentStateStore.setConnectionState(ConnectionState.RECONNECTING)
        reconnectAttempt++
        val delaySec = min(30, 1 shl min(reconnectAttempt, 5))
        AgentStateStore.setStatus("Reconnecting in ${delaySec}s...")

        mainHandler.postDelayed({
            val ep = currentEndpoint
            if (!manualDisconnect && ep != null) {
                val cred = currentCredential ?: storage.loadCredential()
                connect(ep, cred, currentOffer)
            }
        }, delaySec * 1000L)
    }

    fun send(json: JSONObject) {
        val payload = json.toString()
        socket?.send(payload)
    }

    fun sendHello() {
        val snap = buildSnapshot()
        send(BrahmaProtocol.hello(snap))
        AgentStateStore.setStatus("Awaiting approval...")
    }

    fun sendPairRequest(offer: PairingOffer) {
        val snap = buildSnapshot()
        val payload = JSONObject().apply {
            put("pairing_token", offer.pairingToken)
            put("pairing_code", offer.pairingCode)
            put("device", snap.toJson())
        }
        send(BrahmaProtocol.envelope(BrahmaProtocol.PAIR_REQUEST, payload))
        AgentStateStore.setStatus("Pairing request sent")
    }

    fun sendAuthenticate(credential: DeviceCredential) {
        send(BrahmaProtocol.authenticate(credential))
        AgentStateStore.setStatus("Authenticating...")
    }

    fun sendChatMessage(text: String) {
        val userMsg = ChatMessage(role = "user", text = text)
        AgentStateStore.addChatMessage(userMsg)
        send(BrahmaProtocol.chatMessage(text, role = "user"))
        AgentStateStore.setStatus("Sent voice command")
    }

    private fun buildSnapshot(): DeviceSnapshot {
        val infoProvider = com.brahma.connect.device.AndroidDeviceInfoProvider(context)
        val snap = infoProvider.snapshot
        return DeviceSnapshot(
            deviceId = currentCredential?.deviceId ?: getDeviceId(),
            deviceName = snap.deviceName,
            androidVersion = snap.androidVersion,
            batteryPercentage = snap.batteryPercentage,
            charging = snap.charging,
            wifiEnabled = snap.wifiEnabled,
            capabilities = BrahmaConnectCapabilities.INITIAL,
            permissions = listOf("camera", "microphone", "notifications", "accessibility")
        )
    }

    private fun getDeviceId(): String {
        val saved = storage.loadCredential()?.deviceId
        if (saved != null) return saved
        val uuid = UUID.randomUUID().toString().take(12)
        return "android-$uuid"
    }

    private fun handleCommandMessage(root: JSONObject) {
        val requestId = root.optString("request_id", UUID.randomUUID().toString())
        val payload = root.optJSONObject("payload") ?: JSONObject()
        val action = payload.optString("action")
        val parameters = mutableMapOf<String, Any?>()
        val paramsJson = payload.optJSONObject("parameters")
        paramsJson?.let {
            val keys = it.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                parameters[k] = it.get(k)
            }
        }

        AgentStateStore.addLog("Executing command: $action")
        val result = commandHandler.handle(action, parameters)

        val response = JSONObject().apply {
            put("type", BrahmaProtocol.RESULT)
            put("request_id", requestId)
            put("payload", result.toJson())
        }
        send(response)
    }

    private fun handlePairApproved(payload: JSONObject) {
        val device = payload.optJSONObject("device")
        val deviceId = device?.optString("device_id") ?: payload.optString("device_id")
        val deviceSecret = device?.optString("device_secret") ?: payload.optString("device_secret")
        val ep = currentEndpoint

        if (deviceId.isNotBlank() && deviceSecret.isNotBlank() && ep != null) {
            val cred = DeviceCredential(
                deviceId = deviceId,
                deviceSecret = deviceSecret,
                deviceName = device?.optString("name") ?: "Android",
                gatewayHost = ep.host,
                gatewayPort = ep.port,
                pairedAt = System.currentTimeMillis().toString()
            )
            storage.saveCredential(cred)
            currentCredential = cred
            AgentStateStore.setCredential(cred)
            AgentStateStore.setStatus("Pairing approved!")
            AgentStateStore.addLog("Pairing approved by Brahma PC")

            // Send authenticate
            sendAuthenticate(cred)
        }
    }

    private inner class BrahmaSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            reconnectAttempt = 0
            AgentStateStore.setConnectionState(ConnectionState.CONNECTED)
            AgentStateStore.setStatus("Connected to Brahma")
            AgentStateStore.addLog("Connected to ${currentEndpoint?.host}:${currentEndpoint?.port}")

            val cred = currentCredential ?: storage.loadCredential()
            if (cred != null) {
                sendAuthenticate(cred)
            } else if (currentOffer != null) {
                sendPairRequest(currentOffer!!)
            } else {
                sendHello()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = JSONObject(text)
                val type = root.optString("type")
                val payload = root.optJSONObject("payload") ?: JSONObject()

                when (type) {
                    BrahmaProtocol.PAIR_APPROVED -> handlePairApproved(payload)
                    BrahmaProtocol.PING -> send(JSONObject().apply { put("type", BrahmaProtocol.PONG) })
                    BrahmaProtocol.EXECUTE -> handleCommandMessage(root)
                    BrahmaProtocol.CHAT_MESSAGE -> {
                        val replyText = payload.optString("text")
                        val role = payload.optString("role", "assistant")
                        if (replyText.isNotBlank()) {
                            val msg = ChatMessage(role = role, text = replyText)
                            AgentStateStore.addChatMessage(msg)
                            // Trigger Voice Response (TTS) as requested by the user!
                            BrahmaVoiceManager.speak(replyText)
                        }
                    }
                    BrahmaProtocol.AUTHENTICATE -> {
                        val success = payload.optBoolean("success", true)
                        if (success) {
                            AgentStateStore.setStatus("Authenticated & Active")
                            AgentStateStore.addLog("Authenticated with Brahma Gateway")
                        }
                    }
                }
            } catch (e: Exception) {
                AgentStateStore.addLog("Socket message parse error: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            AgentStateStore.setConnectionState(ConnectionState.DISCONNECTED)
            AgentStateStore.setStatus("Socket closing: $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            AgentStateStore.setConnectionState(ConnectionState.DISCONNECTED)
            AgentStateStore.setStatus("Disconnected: $reason")
            reconnectLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            AgentStateStore.setError(t.localizedMessage)
            AgentStateStore.setConnectionState(ConnectionState.DISCONNECTED)
            AgentStateStore.setStatus("Connection failed: ${t.localizedMessage}")
            AgentStateStore.addLog("Socket failure: ${t.message}")
            reconnectLater()
        }
    }
}
