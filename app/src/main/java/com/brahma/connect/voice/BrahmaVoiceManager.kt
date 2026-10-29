package com.brahma.connect.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.brahma.connect.core.AgentStateStore
import com.brahma.connect.core.VoiceState
import java.util.Locale

object BrahmaVoiceManager {

    private var context: Context? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var onSpeechResultListener: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isTtsReady = false

    fun init(appContext: Context) {
        context = appContext.applicationContext

        mainHandler.post {
            // Initialize TTS
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        isTtsReady = true
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            AgentStateStore.setVoiceState(VoiceState.SPEAKING)
                        }

                        override fun onDone(utteranceId: String?) {
                            AgentStateStore.setVoiceState(VoiceState.IDLE)
                        }

                        override fun onError(utteranceId: String?) {
                            AgentStateStore.setVoiceState(VoiceState.IDLE)
                        }
                    })
                }
            }
        }
    }

    fun setSpeechResultListener(listener: (String) -> Unit) {
        onSpeechResultListener = listener
    }

    fun speak(text: String) {
        mainHandler.post {
            if (!isTtsReady || tts == null) {
                AgentStateStore.addLog("TTS not ready yet")
                return@post
            }
            AgentStateStore.setVoiceState(VoiceState.SPEAKING)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "brahma_reply_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            AgentStateStore.setVoiceState(VoiceState.IDLE)
        }
    }

    fun startListening(onResult: ((String) -> Unit)? = null) {
        val ctx = context ?: return
        if (onResult != null) {
            onSpeechResultListener = onResult
        }

        mainHandler.post {
            stopSpeaking()

            // Vibrate short pulse
            vibrate(50)

            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    AgentStateStore.setVoiceState(VoiceState.LISTENING)
                    AgentStateStore.setStatus("Listening to your voice...")
                    AgentStateStore.setLiveSpokenText("")
                }

                override fun onBeginningOfSpeech() {
                    AgentStateStore.setVoiceState(VoiceState.LISTENING)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Could feed real audio levels into 3D reactor WebGL background if desired!
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    AgentStateStore.setVoiceState(VoiceState.PROCESSING)
                    AgentStateStore.setStatus("Processing voice...")
                }

                override fun onError(error: Int) {
                    AgentStateStore.setVoiceState(VoiceState.IDLE)
                    AgentStateStore.setStatus("Voice idle")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    AgentStateStore.setLiveSpokenText(text)
                    AgentStateStore.setVoiceState(VoiceState.PROCESSING)

                    if (text.isNotBlank()) {
                        AgentStateStore.setStatus("Recognized: $text")
                        onSpeechResultListener?.invoke(text)
                    } else {
                        AgentStateStore.setVoiceState(VoiceState.IDLE)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim() ?: ""
                    if (partial.isNotBlank()) {
                        AgentStateStore.setLiveSpokenText(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            AgentStateStore.setVoiceState(VoiceState.IDLE)
        }
    }

    private fun vibrate(millis: Long) {
        val v = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        v?.vibrate(millis)
    }

    fun destroy() {
        mainHandler.post {
            tts?.shutdown()
            speechRecognizer?.destroy()
            tts = null
            speechRecognizer = null
        }
    }
}
