package com.brahma.connect

import android.app.Application
import com.brahma.connect.network.BrahmaWebSocketClient
import com.brahma.connect.voice.BrahmaVoiceManager

class BrahmaConnectApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize Speech and TextToSpeech
        BrahmaVoiceManager.init(this)
    }

    companion object {
        lateinit var instance: BrahmaConnectApplication
            private set

        var webSocketClient: BrahmaWebSocketClient? = null
    }
}
