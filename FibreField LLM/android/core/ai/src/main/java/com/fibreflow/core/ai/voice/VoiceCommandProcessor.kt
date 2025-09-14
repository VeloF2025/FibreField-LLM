package com.fibreflow.core.ai.voice

import com.fibreflow.core.common.result.Result
import timber.log.Timber
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice Command Processor for FibreField Technician Voice Commands
 *
 * Processes natural language speech input and converts to structured commands.
 * Supports installation workflows, navigation, and system control commands.
 *
 * Features:
 * - Natural language processing for technician commands
 * - Command validation and confidence scoring
 * - Multi-language command recognition
 * - Context-aware command interpretation
 * - Fallback handling for unclear commands
 */
@Singleton
class VoiceCommandProcessor @Inject constructor() {

    companion object {
        private const val TAG = "VoiceCommandProcessor"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f

        // Command patterns for different languages
        private val DROP_NUMBER_PATTERN = Pattern.compile(
            "(?:drop|number|num|id)?\\s*(\\d{4,6})",
            Pattern.CASE_INSENSITIVE
        )

        private val STEP_COMMANDS = mapOf(
            "next" to InstallationCommand.NEXT_STEP,
            "previous" to InstallationCommand.PREVIOUS_STEP,
            "back" to InstallationCommand.PREVIOUS_STEP,
            "skip" to InstallationCommand.SKIP_STEP,
            "retry" to InstallationCommand.RETRY_PHOTO,
            "retake" to InstallationCommand.RETRY_PHOTO,
            "complete" to InstallationCommand.COMPLETE_INSTALLATION,
            "finish" to InstallationCommand.COMPLETE_INSTALLATION,
            "done" to InstallationCommand.COMPLETE_INSTALLATION
        )

        private val NAVIGATION_COMMANDS = mapOf(
            "navigate" to NavigationCommand.START_NAVIGATION,
            "directions" to NavigationCommand.START_NAVIGATION,
            "route" to NavigationCommand.START_NAVIGATION,
            "find" to NavigationCommand.FIND_LOCATION,
            "locate" to NavigationCommand.FIND_LOCATION,
            "where" to NavigationCommand.FIND_LOCATION
        )

        private val SYSTEM_COMMANDS = mapOf(
            "help" to SystemCommand.SHOW_HELP,
            "status" to SystemCommand.SHOW_STATUS,
            "pause" to SystemCommand.PAUSE_WORKFLOW,
            "resume" to SystemCommand.RESUME_WORKFLOW,
            "stop" to SystemCommand.STOP_WORKFLOW,
            "cancel" to SystemCommand.CANCEL_OPERATION,
            "settings" to SystemCommand.OPEN_SETTINGS
        )
    }

    /**
     * Parse speech input into structured voice command
     */
    fun parseCommand(speechText: String, confidence: Float): VoiceCommand {
        try {
            Timber.d("$TAG: Parsing command: '$speechText' (confidence: ${confidence * 100}%)")

            if (confidence < MIN_CONFIDENCE_THRESHOLD) {
                Timber.w("$TAG: Low confidence command, returning unknown")
                return VoiceCommand.Unknown(speechText, confidence)
            }

            val normalizedText = normalizeText(speechText)

            // Try different command types in order of priority
            return when {
                // Installation commands
                isInstallationCommand(normalizedText) ->
                    parseInstallationCommand(normalizedText, confidence)

                // Navigation commands
                isNavigationCommand(normalizedText) ->
                    parseNavigationCommand(normalizedText, confidence)

                // System commands
                isSystemCommand(normalizedText) ->
                    parseSystemCommand(normalizedText, confidence)

                // Drop number commands
                containsDropNumber(normalizedText) ->
                    parseDropCommand(normalizedText, confidence)

                // Default to unknown
                else -> {
                    Timber.i("$TAG: No matching command pattern found")
                    VoiceCommand.Unknown(speechText, confidence)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to parse voice command")
            return VoiceCommand.Error(speechText, confidence, e.message ?: "Parse error")
        }
    }

    /**
     * Validate command confidence and provide feedback
     */
    fun validateCommand(command: VoiceCommand): CommandValidation {
        return when (command) {
            is VoiceCommand.Installation -> {
                if (command.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                    CommandValidation.Valid(command)
                } else {
                    CommandValidation.NeedsConfirmation(
                        command,
                        "I heard '${command.action}'. Is that correct?"
                    )
                }
            }

            is VoiceCommand.Navigation -> {
                if (command.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                    CommandValidation.Valid(command)
                } else {
                    CommandValidation.NeedsConfirmation(
                        command,
                        "I heard navigation to '${command.destination}'. Is that correct?"
                    )
                }
            }

            is VoiceCommand.System -> {
                // System commands are usually high confidence
                CommandValidation.Valid(command)
            }

            is VoiceCommand.DropOperation -> {
                if (command.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                    CommandValidation.Valid(command)
                } else {
                    CommandValidation.NeedsConfirmation(
                        command,
                        "I heard drop ${command.dropNumber}. Is that correct?"
                    )
                }
            }

            is VoiceCommand.Unknown -> {
                CommandValidation.Invalid(
                    command,
                    "I didn't understand that command. Try saying 'help' for available commands."
                )
            }

            is VoiceCommand.Error -> {
                CommandValidation.Invalid(
                    command,
                    "There was an error processing your command: ${command.errorMessage}"
                )
            }
        }
    }

    /**
     * Get available voice commands for help display
     */
    fun getAvailableCommands(): List<String> {
        return listOf(
            "Installation Commands:",
            "• 'next step' or 'continue'",
            "• 'previous step' or 'go back'",
            "• 'retry photo' or 'retake'",
            "• 'complete installation'",
            "",
            "Navigation Commands:",
            "• 'navigate to drop' or 'find location'",
            "• 'show route' or 'directions'",
            "",
            "System Commands:",
            "• 'help' or 'show help'",
            "• 'status' or 'show status'",
            "• 'pause' or 'resume'",
            "• 'settings' or 'open settings'",
            "",
            "Drop Commands:",
            "• 'drop 12345' or 'find drop 12345'"
        )
    }

    // Private parsing methods

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ") // Remove punctuation
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }

    private fun isInstallationCommand(text: String): Boolean {
        return STEP_COMMANDS.keys.any { text.contains(it) } ||
               text.contains("step") ||
               text.contains("photo") ||
               text.contains("install")
    }

    private fun isNavigationCommand(text: String): Boolean {
        return NAVIGATION_COMMANDS.keys.any { text.contains(it) } ||
               text.contains("navigate") ||
               text.contains("route") ||
               text.contains("directions")
    }

    private fun isSystemCommand(text: String): Boolean {
        return SYSTEM_COMMANDS.keys.any { text.contains(it) } ||
               text.contains("help") ||
               text.contains("status") ||
               text.contains("settings")
    }

    private fun containsDropNumber(text: String): Boolean {
        return DROP_NUMBER_PATTERN.matcher(text).find() ||
               text.contains("drop") && text.matches(Regex(".*\\d{4,6}.*"))
    }

    private fun parseInstallationCommand(text: String, confidence: Float): VoiceCommand {
        // Find matching command
        val command = STEP_COMMANDS.entries.firstOrNull { text.contains(it.key) }

        return if (command != null) {
            VoiceCommand.Installation(command.value, confidence)
        } else {
            // Try to infer from context
            when {
                text.contains("next") || text.contains("continue") ->
                    VoiceCommand.Installation(InstallationCommand.NEXT_STEP, confidence)
                text.contains("back") || text.contains("previous") ->
                    VoiceCommand.Installation(InstallationCommand.PREVIOUS_STEP, confidence)
                text.contains("retry") || text.contains("retake") ->
                    VoiceCommand.Installation(InstallationCommand.RETRY_PHOTO, confidence)
                text.contains("complete") || text.contains("finish") || text.contains("done") ->
                    VoiceCommand.Installation(InstallationCommand.COMPLETE_INSTALLATION, confidence)
                else ->
                    VoiceCommand.Unknown(text, confidence)
            }
        }
    }

    private fun parseNavigationCommand(text: String, confidence: Float): VoiceCommand {
        val command = NAVIGATION_COMMANDS.entries.firstOrNull { text.contains(it.key) }

        return if (command != null) {
            VoiceCommand.Navigation(command.value, extractDestination(text), confidence)
        } else {
            VoiceCommand.Navigation(NavigationCommand.START_NAVIGATION, extractDestination(text), confidence)
        }
    }

    private fun parseSystemCommand(text: String, confidence: Float): VoiceCommand {
        val command = SYSTEM_COMMANDS.entries.firstOrNull { text.contains(it.key) }

        return if (command != null) {
            VoiceCommand.System(command.value, confidence)
        } else {
            // Default to help for unrecognized system commands
            VoiceCommand.System(SystemCommand.SHOW_HELP, confidence)
        }
    }

    private fun parseDropCommand(text: String, confidence: Float): VoiceCommand {
        val matcher = DROP_NUMBER_PATTERN.matcher(text)
        val dropNumber = if (matcher.find()) {
            matcher.group(1)
        } else {
            // Try to extract any number sequence
            Regex("\\d{4,6}").find(text)?.value
        }

        return if (dropNumber != null) {
            VoiceCommand.DropOperation(DropCommand.FIND_DROP, dropNumber, confidence)
        } else {
            VoiceCommand.Unknown(text, confidence)
        }
    }

    private fun extractDestination(text: String): String {
        // Try to extract location/destination from navigation commands
        return when {
            text.contains("drop") -> {
                val matcher = DROP_NUMBER_PATTERN.matcher(text)
                if (matcher.find()) "Drop ${matcher.group(1)}" else "Drop location"
            }
            text.contains("next") -> "Next drop"
            else -> "Specified location"
        }
    }
}

/**
 * Voice command types and their data structures
 */
sealed class VoiceCommand {
    abstract val originalText: String
    abstract val confidence: Float

    data class Installation(
        val action: InstallationCommand,
        override val confidence: Float,
        override val originalText: String = ""
    ) : VoiceCommand()

    data class Navigation(
        val action: NavigationCommand,
        val destination: String,
        override val confidence: Float,
        override val originalText: String = ""
    ) : VoiceCommand()

    data class System(
        val action: SystemCommand,
        override val confidence: Float,
        override val originalText: String = ""
    ) : VoiceCommand()

    data class DropOperation(
        val action: DropCommand,
        val dropNumber: String,
        override val confidence: Float,
        override val originalText: String = ""
    ) : VoiceCommand()

    data class Unknown(
        override val originalText: String,
        override val confidence: Float
    ) : VoiceCommand()

    data class Error(
        override val originalText: String,
        override val confidence: Float,
        val errorMessage: String
    ) : VoiceCommand()
}

/**
 * Installation workflow commands
 */
enum class InstallationCommand {
    NEXT_STEP,
    PREVIOUS_STEP,
    SKIP_STEP,
    RETRY_PHOTO,
    COMPLETE_INSTALLATION
}

/**
 * Navigation and location commands
 */
enum class NavigationCommand {
    START_NAVIGATION,
    FIND_LOCATION,
    SHOW_ROUTE
}

/**
 * System control commands
 */
enum class SystemCommand {
    SHOW_HELP,
    SHOW_STATUS,
    PAUSE_WORKFLOW,
    RESUME_WORKFLOW,
    STOP_WORKFLOW,
    CANCEL_OPERATION,
    OPEN_SETTINGS
}

/**
 * Drop management commands
 */
enum class DropCommand {
    FIND_DROP,
    VALIDATE_DROP,
    START_INSTALLATION
}

/**
 * Command validation results
 */
sealed class CommandValidation {
    data class Valid(val command: VoiceCommand) : CommandValidation()
    data class NeedsConfirmation(val command: VoiceCommand, val confirmationMessage: String) : CommandValidation()
    data class Invalid(val command: VoiceCommand, val errorMessage: String) : CommandValidation()
}