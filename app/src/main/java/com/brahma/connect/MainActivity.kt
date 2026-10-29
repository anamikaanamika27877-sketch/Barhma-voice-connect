package com.brahma.connect

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.brahma.connect.network.BrahmaGatewayDiscovery
import com.brahma.connect.pairing.PairingStorage
import com.brahma.connect.ui.BrahmaConnectApp
import com.brahma.connect.ui.theme.BrahmaConnectTheme
import com.brahma.connect.voice.BrahmaVoiceManager

class MainActivity : ComponentActivity() {

    private lateinit var storage: PairingStorage
    private var discovery: BrahmaGatewayDiscovery? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // State will reactively update
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // State will reactively update
    }

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startGatewayService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = PairingStorage(this)
        discovery = BrahmaGatewayDiscovery(this)

        ensurePermissions()
        startGatewayService()

        // Handle wake-word intent
        if (intent?.getBooleanExtra("WAKE_WORD_ACTIVATED", false) == true) {
            BrahmaVoiceManager.startListening { query ->
                BrahmaConnectApplication.webSocketClient?.sendChatMessage(query)
            }
        }

        setContent {
            BrahmaConnectTheme {
                BrahmaConnectApp(
                    onRequestCameraPermission = {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestMicrophonePermission = {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStartService = {
                        startGatewayService()
                    },
                    onConnectGateway = { endpoint ->
                        BrahmaConnectApplication.webSocketClient?.connect(endpoint)
                    },
                    onSendPairRequest = { offer ->
                        BrahmaConnectApplication.webSocketClient?.sendPairRequest(offer)
                    },
                    onDisconnect = {
                        BrahmaConnectApplication.webSocketClient?.disconnect()
                    },
                    onSendVoiceMessage = { text ->
                        BrahmaConnectApplication.webSocketClient?.sendChatMessage(text)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("WAKE_WORD_ACTIVATED", false)) {
            BrahmaVoiceManager.startListening { query ->
                BrahmaConnectApplication.webSocketClient?.sendChatMessage(query)
            }
        }
    }

    private fun ensurePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != 0) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != 0) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 0) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startGatewayService() {
        val intent = Intent(this, BrahmaConnectForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discovery?.stop()
    }
}
