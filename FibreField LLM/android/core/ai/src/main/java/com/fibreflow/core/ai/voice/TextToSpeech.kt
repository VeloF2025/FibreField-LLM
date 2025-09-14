package com.fibreflow.core.ai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Text-to-Speech Service for FibreField Technician Voice Guidance
 *
 * Provides voice output for AI guidance, instructions, and feedback.
 * Supports installation workflows, error messages, and status updates.
 *
 * Features:
 * - High-quality voice synthesis
 * - Queue management for multiple utterances
 * - Speech rate and pitch control
 * - Language support
 * - Audio focus management
 */
@Singleton
class TextToSpeech @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "TextToSpeech"
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val DEFAULT_PITCH = 1.0f
        private const val MAX_QUEUE_SIZE = 10
    }

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val utteranceQueue = mutableListOf<PendingUtterance>()
    private var isSpeaking = false

    /**
     * Initialize TTS engine
     */
    suspend fun initialize(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            textToSpeech = TextToSpeech(context) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        Timber.i("$TAG: TTS engine initialized successfully")

                        // Configure TTS settings
                        textToSpeech?.apply {
                            language = Locale.getDefault()
                            setSpeechRate(DEFAULT_SPEECH_RATE)
                            setPitch(DEFAULT_PITCH)
                            setOnUtteranceProgressListener(createUtteranceListener())
                        }

                        isInitialized = true
                        continuation.resume(Result.Success(Unit))
                    }

                    TextToSpeech.ERROR -> {
                        val error = Exception("TTS engine initialization failed")
                        Timber.e(error, "$TAG: TTS initialization failed")
                        continuation.resume(Result.Error(error))
                    }

                    else -> {
                        val error = Exception("TTS engine initialization failed with unknown status: $status")
                        Timber.e(error, "$TAG: TTS initialization failed")
                        continuation.resume(Result.Error(error))
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to create TTS engine")
            continuation.resume(Result.Error(e))
        }
    }

    /**
     * Speak text with voice guidance
     */
    suspend fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL): Result<Unit> {
        return try {
            ensureInitialized()

            val utterance = PendingUtterance(
                text = text,
                priority = priority,
                utteranceId = generateUtteranceId()
            )

            // Add to queue with priority handling
            addToQueue(utterance)

            // Process queue if not currently speaking
            if (!isSpeaking) {
                processQueue()
            }

            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to speak text")
            Result.Error(e)
        }
    }

    /**
     * Speak installation guidance with contextual information
     */
    suspend fun speakGuidance(
        message: String,
        stepNumber: Int? = null,
        totalSteps: Int? = null,
        isError: Boolean = false
    ): Result<Unit> {
        val formattedMessage = buildString {
            if (stepNumber != null && totalSteps != null) {
                append("Step $stepNumber of $totalSteps. ")
            }
            append(message)
        }

        val priority = if (isError) SpeechPriority.HIGH else SpeechPriority.NORMAL
        return speak(formattedMessage, priority)
    }

    /**
     * Stop current speech and clear queue
     */
    fun stop(): Result<Unit> {
        return try {
            textToSpeech?.stop()
            utteranceQueue.clear()
            isSpeaking = false
            Timber.d("$TAG: Speech stopped and queue cleared")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to stop speech")
            Result.Error(e)
        }
    }

    /**
     * Pause current speech (if supported)
     */
    fun pause(): Result<Unit> {
        return try {
            // Note: Not all TTS engines support pause/resume
            Timber.d("$TAG: Speech pause requested (may not be supported)")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to pause speech")
            Result.Error(e)
        }
    }

    /**
     * Resume paused speech (if supported)
     */
    fun resume(): Result<Unit> {
        return try {
            // Note: Not all TTS engines support pause/resume
            Timber.d("$TAG: Speech resume requested (may not be supported)")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to resume speech")
            Result.Error(e)
        }
    }

    /**
     * Set speech rate (0.5 to 2.0)
     */
    fun setSpeechRate(rate: Float): Result<Unit> {
        return try {
            val clampedRate = rate.coerceIn(0.5f, 2.0f)
            textToSpeech?.setSpeechRate(clampedRate)
            Timber.d("$TAG: Speech rate set to $clampedRate")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to set speech rate")
            Result.Error(e)
        }
    }

    /**
     * Set speech pitch (0.5 to 2.0)
     */
    fun setPitch(pitch: Float): Result<Unit> {
        return try {
            val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
            textToSpeech?.setPitch(clampedPitch)
            Timber.d("$TAG: Speech pitch set to $clampedPitch")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to set speech pitch")
            Result.Error(e)
        }
    }

    /**
     * Set language for speech synthesis
     */
    fun setLanguage(language: String): Result<Unit> {
        return try {
            val locale = Locale(language)
            val result = textToSpeech?.setLanguage(locale)

            when (result) {
                TextToSpeech.LANG_AVAILABLE -> {
                    Timber.d("$TAG: Language set to $language")
                    Result.Success(Unit)
                }

                TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
                    Timber.w("$TAG: Language $language available but country variant not found")
                    Result.Success(Unit)
                }

                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    Timber.w("$TAG: Language $language available but variant not found")
                    Result.Success(Unit)
                }

                TextToSpeech.LANG_MISSING_DATA -> {
                    val error = Exception("Language $language missing data")
                    Timber.e(error, "$TAG: Language data missing")
                    Result.Error(error)
                }

                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    val error = Exception("Language $language not supported")
                    Timber.e(error, "$TAG: Language not supported")
                    Result.Error(error)
                }

                else -> {
                    val error = Exception("Unknown language setting result: $result")
                    Timber.e(error, "$TAG: Unknown language result")
                    Result.Error(error)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to set language")
            Result.Error(e)
        }
    }

    /**
     * Check if TTS is currently speaking
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * Get available languages
     */
    fun getAvailableLanguages(): Set<Locale> {
        return textToSpeech?.availableLanguages ?: emptySet()
    }

    /**
     * Shutdown TTS engine and free resources
     */
    fun shutdown() {
        try {
            textToSpeech?.apply {
                stop()
                shutdown()
            }
            textToSpeech = null
            utteranceQueue.clear()
            isInitialized = false
            isSpeaking = false
            Timber.i("$TAG: TTS engine shutdown complete")

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during TTS shutdown")
        }
    }

    // Private helper methods

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("TTS engine not initialized. Call initialize() first.")
        }
    }

    private fun addToQueue(utterance: PendingUtterance) {
        // Remove low priority items if queue is full
        if (utteranceQueue.size >= MAX_QUEUE_SIZE) {
            utteranceQueue.removeAll { it.priority == SpeechPriority.LOW }
        }

        // If still full, remove oldest item
        if (utteranceQueue.size >= MAX_QUEUE_SIZE) {
            utteranceQueue.removeAt(0)
        }

        utteranceQueue.add(utterance)
        Timber.d("$TAG: Added utterance to queue: ${utterance.text.take(50)}...")
    }

    private fun processQueue() {
        if (utteranceQueue.isEmpty() || isSpeaking) {
            return
        }

        val nextUtterance = utteranceQueue.removeAt(0)
        speakUtterance(nextUtterance)
    }

    private fun speakUtterance(utterance: PendingUtterance) {
        try {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utterance.utteranceId)
            }

            val result = textToSpeech?.speak(
                utterance.text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utterance.utteranceId
            )

            if (result == TextToSpeech.SUCCESS) {
                isSpeaking = true
                Timber.d("$TAG: Started speaking: ${utterance.text.take(50)}...")
            } else {
                Timber.e("$TAG: Failed to speak utterance, result: $result")
                processQueue() // Try next item
            }

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error speaking utterance")
            processQueue() // Try next item
        }
    }

    private fun createUtteranceListener() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Timber.d("$TAG: Utterance started: $utteranceId")
            isSpeaking = true
        }

        override fun onDone(utteranceId: String?) {
            Timber.d("$TAG: Utterance completed: $utteranceId")
            isSpeaking = false
            processQueue() // Process next item in queue
        }

        override fun onError(utteranceId: String?) {
            Timber.e("$TAG: Utterance error: $utteranceId")
            isSpeaking = false
            processQueue() // Process next item in queue
        }
    }

    private fun generateUtteranceId(): String {
        return "tts_${System.currentTimeMillis()}_${utteranceQueue.size}"
    }
}

/**
 * Speech priority levels for queue management
 */
enum class SpeechPriority {
    LOW,     // Background information
    NORMAL,  // Standard guidance
    HIGH     // Errors and critical information
}

/**
 * Pending utterance data class
 */
private data class PendingUtterance(
    val text: String,
    val priority: SpeechPriority,
    val utteranceId: String
)