package com.fibreflow.core.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speech Recognition Service for FibreField Technician Voice Commands
 *
 * Provides real-time speech-to-text functionality for hands-free operation.
 * Supports installation commands, navigation, and status queries.
 *
 * Features:
 * - Continuous speech recognition
 * - Voice command processing
 * - Confidence scoring
 * - Error handling and recovery
 * - Multi-language support
 */
@Singleton
class SpeechRecognizer @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "SpeechRecognizer"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.7f
        private const val MAX_RETRIES = 3
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var retryCount = 0

    /**
     * Initialize speech recognition service
     */
    fun initialize(): Result<Unit> {
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return Result.Error(IllegalStateException("Speech recognition not available on this device"))
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }

            Timber.i("$TAG: Speech recognizer initialized successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize speech recognizer")
            Result.Error(e)
        }
    }

    /**
     * Start listening for speech input
     */
    fun startListening(language: String = Locale.getDefault().language): Result<Unit> {
        return try {
            if (isListening) {
                Timber.w("$TAG: Already listening, ignoring start request")
                return Result.Success(Unit)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            retryCount = 0

            Timber.d("$TAG: Started listening for speech input")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to start listening")
            Result.Error(e)
        }
    }

    /**
     * Stop listening for speech input
     */
    fun stopListening(): Result<Unit> {
        return try {
            speechRecognizer?.stopListening()
            isListening = false
            Timber.d("$TAG: Stopped listening for speech input")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to stop listening")
            Result.Error(e)
        }
    }

    /**
     * Cancel speech recognition
     */
    fun cancel(): Result<Unit> {
        return try {
            speechRecognizer?.cancel()
            isListening = false
            retryCount = 0
            Timber.d("$TAG: Speech recognition cancelled")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to cancel speech recognition")
            Result.Error(e)
        }
    }

    /**
     * Check if currently listening
     */
    fun isListening(): Boolean = isListening

    /**
     * Destroy speech recognizer and free resources
     */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            retryCount = 0
            Timber.i("$TAG: Speech recognizer destroyed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error destroying speech recognizer")
        }
    }

    /**
     * Create recognition listener for handling speech events
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.d("$TAG: Ready for speech")
            retryCount = 0
        }

        override fun onBeginningOfSpeech() {
            Timber.d("$TAG: Beginning of speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // RMS change - can be used for visual feedback
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            Timber.d("$TAG: Audio buffer received")
        }

        override fun onEndOfSpeech() {
            Timber.d("$TAG: End of speech detected")
        }

        override fun onError(error: Int) {
            val errorMessage = getErrorMessage(error)
            Timber.e("$TAG: Speech recognition error: $errorMessage (code: $error)")

            isListening = false

            // Retry on certain errors
            if (shouldRetry(error) && retryCount < MAX_RETRIES) {
                retryCount++
                Timber.i("$TAG: Retrying speech recognition (attempt $retryCount/$MAX_RETRIES)")
                startListening()
            }
        }

        override fun onResults(results: Bundle?) {
            Timber.d("$TAG: Speech recognition results received")

            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (matches.isNotEmpty()) {
                    val bestMatch = matches[0]
                    val confidence = confidences?.getOrNull(0) ?: 0f

                    Timber.i("$TAG: Recognized: '$bestMatch' (confidence: ${confidence * 100}%)")

                    if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                        // Process the recognized speech
                        processRecognizedSpeech(bestMatch, confidence)
                    } else {
                        Timber.w("$TAG: Recognition confidence too low: ${confidence * 100}%")
                    }
                }
            }

            isListening = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Handle partial results for real-time feedback
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { partial ->
                if (partial.isNotEmpty()) {
                    Timber.d("$TAG: Partial result: ${partial[0]}")
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Timber.d("$TAG: Speech recognition event: $eventType")
        }
    }

    /**
     * Process recognized speech and convert to voice commands
     */
    private fun processRecognizedSpeech(text: String, confidence: Float) {
        try {
            val command = VoiceCommandProcessor.parseCommand(text, confidence)
            Timber.i("$TAG: Processed command: ${command.type} (confidence: ${confidence * 100}%)")

            // Here you would typically emit the command to a flow or callback
            // For now, we'll just log it

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to process recognized speech")
        }
    }

    /**
     * Get human-readable error message
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error (code: $errorCode)"
        }
    }

    /**
     * Determine if we should retry on this error
     */
    private fun shouldRetry(errorCode: Int): Boolean {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> true
            else -> false
        }
    }
}