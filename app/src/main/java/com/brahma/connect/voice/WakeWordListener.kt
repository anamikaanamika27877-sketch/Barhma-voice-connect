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
import com.brahma.connect.MainActivity
import com.brahma.connect.core.AgentStateStore
import java.util.Locale

class WakeWordListener(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wakeWords = listOf("hey brahma", "hay brahma", "brahma", "a brahma", "bramha")

    fun start() {
        if (isListening) return
        isListening = true
        mainHandler.post {
            startInternal()
        }
    }

    fun stop() {
        isListening = false
        mainHandler.post {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
        }
    }

    private fun startInternal() {
        if (!isListening) return

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // Automatically restart listening in background after small delay
                    if (isListening) {
                        mainHandler.postDelayed({
                            if (isListening) startInternal()
                        }, 500)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkMatches(matches)
                    // If wake word was not triggered, keep listening
                    if (isListening) {
                        mainHandler.postDelayed({
                            if (isListening) startInternal()
                        }, 200)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkMatches(matches)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer?.startListening(intent)
        } catch (e: Exception) {
            AgentStateStore.addLog("WakeWord error: ${e.message}")
            if (isListening) {
                mainHandler.postDelayed({ if (isListening) startInternal() }, 1000)
            }
        }
    }

    private fun checkMatches(matches: List<String>?) {
        if (matches == null) return
        for (m in matches) {
            val lower = m.lowercase().trim()
            if (wakeWords.any { lower.contains(it) }) {
                triggerWakeUp(lower)
                break
            }
        }
    }

    private fun triggerWakeUp(detectedPhrase: String) {
        AgentStateStore.addLog("Wake-word detected: '$detectedPhrase'")
        AgentStateStore.setWakeWordDetected(true)

        // Haptic feedback
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(longArrayOf(0, 100, 100, 200), -1)

        // Bring activity to foreground if backgrounded
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("WAKE_WORD_ACTIVATED", true)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}

        onWakeWordDetected()
    }
}
