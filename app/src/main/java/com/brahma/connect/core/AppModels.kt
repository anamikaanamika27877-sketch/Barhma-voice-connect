package com.brahma.connect.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING
}

data class GatewayEndpoint(
    val name: String,
    val host: String,
    val port: Int,
    val online: Boolean
)

data class DeviceCredential(
    val deviceId: String,
    val deviceSecret: String,
    val deviceName: String,
    val gatewayHost: String,
    val gatewayPort: Int,
    val pairedAt: String
)

data class PairingOffer(
    val service: String,
    val host: String,
    val port: Int,
    val pairingToken: String,
    val pairingCode: String,
    val expiresInSeconds: Int,
    val createdAt: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("service", service)
            put("host", host)
            put("port", port)
            put("pairingToken", pairingToken)
            put("pairingCode", pairingCode)
            put("expiresInSeconds", expiresInSeconds)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PairingOffer {
            return PairingOffer(
                service = json.optString("service", "brahma"),
                host = json.optString("host", "127.0.0.1"),
                port = json.optInt("port", 8765),
                pairingToken = json.optString("pairingToken", ""),
                pairingCode = json.optString("pairingCode", ""),
                expiresInSeconds = json.optInt("expiresInSeconds", 300),
                createdAt = json.optString("createdAt", "")
            )
        }
    }
}

data class DeviceSnapshot(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "Android",
    val androidVersion: String,
    val agentVersion: String = "1.0.0",
    val batteryPercentage: Int,
    val charging: Boolean,
    val wifiEnabled: Boolean,
    val capabilities: List<String>,
    val permissions: List<String>,
    val metadata: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("platform", platform)
            put("androidVersion", androidVersion)
            put("agentVersion", agentVersion)
            put("batteryPercentage", batteryPercentage)
            put("charging", charging)
            put("wifiEnabled", wifiEnabled)
            put("capabilities", JSONArray(capabilities))
            put("permissions", JSONArray(permissions))
            put("metadata", JSONObject(metadata))
        }
    }
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent"
)

data class CommandResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val errorCode: String? = null,
    val error: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("success", success)
            put("data", JSONObject(data))
            if (errorCode != null) put("errorCode", errorCode)
            if (error != null) put("error", error)
        }
    }
}

object BrahmaProtocol {
    const val HELLO = "hello"
    const val PAIR_REQUEST = "pair_request"
    const val PAIR_APPROVED = "pair_approved"
    const val AUTHENTICATE = "authenticate"
    const val DEVICE_ONLINE = "device_online"
    const val DEVICE_OFFLINE = "device_offline"
    const val CAPABILITIES = "capabilities"
    const val EXECUTE = "execute"
    const val RESULT = "result"
    const val EVENT = "event"
    const val ERROR = "error"
    const val PING = "ping"
    const val PONG = "pong"
    const val CHAT_MESSAGE = "chat_message"

    fun envelope(type: String, payload: JSONObject, requestId: String = UUID.randomUUID().toString()): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("request_id", requestId)
            put("payload", payload)
        }
    }

    fun hello(snapshot: DeviceSnapshot): JSONObject {
        return envelope(HELLO, JSONObject().apply {
            put("snapshot", snapshot.toJson())
        })
    }

    fun authenticate(credential: DeviceCredential): JSONObject {
        return envelope(AUTHENTICATE, JSONObject().apply {
            put("device_id", credential.deviceId)
            put("device_secret", credential.deviceSecret)
        })
    }

    fun ping(): JSONObject {
        return envelope(PING, JSONObject())
    }

    fun chatMessage(text: String, role: String = "user"): JSONObject {
        return envelope(CHAT_MESSAGE, JSONObject().apply {
            put("text", text)
            put("role", role)
            put("timestamp", System.currentTimeMillis())
        })
    }
}
