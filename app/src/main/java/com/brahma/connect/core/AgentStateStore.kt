package com.brahma.connect.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BrahmaConnectCapabilities {
    val INITIAL = listOf(
        "device_info",
        "battery",
        "flashlight",
        "volume",
        "media",
        "launch_app",
        "accessibility",
        "file_manager",
        "voice_control",
        "chat"
    )
}

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

object AgentStateStore {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _gateway = MutableStateFlow<GatewayEndpoint?>(null)
    val gateway: StateFlow<GatewayEndpoint?> = _gateway.asStateFlow()

    private val _pairingOffer = MutableStateFlow<PairingOffer?>(null)
    val pairingOffer: StateFlow<PairingOffer?> = _pairingOffer.asStateFlow()

    private val _credential = MutableStateFlow<DeviceCredential?>(null)
    val credential: StateFlow<DeviceCredential?> = _credential.asStateFlow()

    private val _statusText = MutableStateFlow("Ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    // Voice & Wake-word specific state
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _wakeWordDetected = MutableStateFlow(false)
    val wakeWordDetected: StateFlow<Boolean> = _wakeWordDetected.asStateFlow()

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun setGateway(endpoint: GatewayEndpoint?) {
        _gateway.value = endpoint
    }

    fun setPairingOffer(offer: PairingOffer?) {
        _pairingOffer.value = offer
    }

    fun setCredential(cred: DeviceCredential?) {
        _credential.value = cred
    }

    fun setStatus(text: String) {
        _statusText.value = text
    }

    fun setError(error: String?) {
        _lastError.value = error
    }

    fun addLog(message: String) {
        val updated = _logs.value.toMutableList()
        if (updated.size >= 100) updated.removeAt(0)
        updated.add(message)
        _logs.value = updated
    }

    fun addChatMessage(msg: ChatMessage) {
        _chatHistory.value = _chatHistory.value + msg
    }

    fun setChatHistory(messages: List<ChatMessage>) {
        _chatHistory.value = messages
    }

    fun setVoiceState(state: VoiceState) {
        _voiceState.value = state
    }

    fun setLiveSpokenText(text: String) {
        _liveSpokenText.value = text
    }

    fun setWakeWordDetected(detected: Boolean) {
        _wakeWordDetected.value = detected
    }
}
