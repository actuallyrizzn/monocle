package com.actuallyrizzn.monocle

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Phone-mic STT using the same Android recognizer Voice KB uses.
 * Glasses audio does not go through this path — Monocle PCM is a WAV Otto transcribes.
 */
class PhoneStt(private val appContext: Context) {
    fun listenBlocking(timeoutMs: Long = 12_000): String {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            return "ERROR: speech recognizer unavailable"
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference("ERROR: no speech")
        val main = Handler(Looper.getMainLooper())
        main.post {
            val rec = SpeechRecognizer.createSpeechRecognizer(appContext)
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    result.set("ERROR: stt $error")
                    rec.destroy()
                    latch.countDown()
                }
                override fun onResults(results: Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    result.set(texts?.firstOrNull()?.trim().orEmpty().ifEmpty { "ERROR: empty" })
                    rec.destroy()
                    latch.countDown()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            rec.startListening(listenIntent())
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            return "ERROR: stt timeout"
        }
        return result.get()
    }

    private fun listenIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_500L)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    2_500L,
                )
            }
        }
    }
}
