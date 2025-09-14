// 🟢 WORKING: String utility extensions for common operations
package com.fibreflow.core.common.extensions

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Check if string is a valid email address
 */
fun String.isValidEmail(): Boolean {
    val pattern = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )
    return pattern.matcher(this).matches()
}

/**
 * Check if string is a valid phone number (basic validation)
 */
fun String.isValidPhoneNumber(): Boolean {
    val cleaned = this.replace(Regex("[^0-9]"), "")
    return cleaned.length in 10..15
}

/**
 * Format phone number with standard formatting
 */
fun String.formatPhoneNumber(): String {
    val cleaned = this.replace(Regex("[^0-9]"), "")
    return when (cleaned.length) {
        10 -> "${cleaned.substring(0, 3)}-${cleaned.substring(3, 6)}-${cleaned.substring(6)}"
        11 -> "+${cleaned.substring(0, 1)} ${cleaned.substring(1, 4)}-${cleaned.substring(4, 7)}-${cleaned.substring(7)}"
        else -> this
    }
}

/**
 * Check if string is a valid drop number format
 */
fun String.isValidDropNumber(): Boolean {
    // Expects format like: PROJ-001-DROP-12345 or similar
    val pattern = Pattern.compile("^[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+$", Pattern.CASE_INSENSITIVE)
    return pattern.matcher(this.trim()).matches() && this.trim().length >= 8
}

/**
 * Sanitize drop number to standard format
 */
fun String.sanitizeDropNumber(): String {
    return this.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "")
}

/**
 * Check if string is a valid serial number
 */
fun String.isValidSerialNumber(): Boolean {
    val cleaned = this.trim()
    return cleaned.length >= 6 && cleaned.matches(Regex("^[A-Za-z0-9]+$"))
}

/**
 * Generate SHA-256 hash of the string
 */
fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Generate MD5 hash of the string
 */
fun String.md5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Truncate string to specified length with ellipsis
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (this.length <= maxLength) {
        this
    } else {
        "${this.take(maxLength - ellipsis.length)}$ellipsis"
    }
}

/**
 * Capitalize first letter of each word
 */
fun String.toTitleCase(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase() else it.toString() 
        }
    }
}

/**
 * Remove all whitespace characters
 */
fun String.removeWhitespace(): String {
    return this.replace(Regex("\\s"), "")
}

/**
 * Check if string contains only alphanumeric characters
 */
fun String.isAlphanumeric(): Boolean {
    return this.matches(Regex("^[A-Za-z0-9]+$"))
}

/**
 * Mask sensitive information (show only first and last few characters)
 */
fun String.mask(visibleChars: Int = 2, maskChar: Char = '*'): String {
    return when {
        this.length <= visibleChars * 2 -> maskChar.toString().repeat(this.length)
        else -> {
            val prefix = this.take(visibleChars)
            val suffix = this.takeLast(visibleChars)
            val maskLength = this.length - (visibleChars * 2)
            "$prefix${maskChar.toString().repeat(maskLength)}$suffix"
        }
    }
}

/**
 * Convert string to snake_case
 */
fun String.toSnakeCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
}

/**
 * Convert string to camelCase
 */
fun String.toCamelCase(): String {
    return this.split(Regex("[^A-Za-z0-9]"))
        .filter { it.isNotEmpty() }
        .mapIndexed { index, word ->
            if (index == 0) {
                word.lowercase()
            } else {
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
        }
        .joinToString("")
}

/**
 * Extract numbers from string
 */
fun String.extractNumbers(): List<Int> {
    val pattern = Pattern.compile("-?\\d+")
    val matcher = pattern.matcher(this)
    val numbers = mutableListOf<Int>()
    
    while (matcher.find()) {
        try {
            numbers.add(matcher.group().toInt())
        } catch (e: NumberFormatException) {
            // Skip invalid numbers
        }
    }
    
    return numbers
}

/**
 * Extract decimal numbers from string
 */
fun String.extractDecimals(): List<Double> {
    val pattern = Pattern.compile("-?\\d+(\\.\\d+)?")
    val matcher = pattern.matcher(this)
    val numbers = mutableListOf<Double>()
    
    while (matcher.find()) {
        try {
            numbers.add(matcher.group().toDouble())
        } catch (e: NumberFormatException) {
            // Skip invalid numbers
        }
    }
    
    return numbers
}

/**
 * Check if string represents a valid number
 */
fun String.isNumeric(): Boolean {
    return this.toDoubleOrNull() != null
}

/**
 * Safe conversion to Int with default value
 */
fun String.toIntOrDefault(default: Int = 0): Int {
    return this.toIntOrNull() ?: default
}

/**
 * Safe conversion to Double with default value
 */
fun String.toDoubleOrDefault(default: Double = 0.0): Double {
    return this.toDoubleOrNull() ?: default
}

/**
 * Check if string matches any of the provided patterns
 */
fun String.matchesAny(vararg patterns: String): Boolean {
    return patterns.any { pattern ->
        Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(this).matches()
    }
}

/**
 * Remove HTML tags from string
 */
fun String.stripHtml(): String {
    return this.replace(Regex("<[^>]*>"), "")
}

/**
 * Encode string for URL
 */
fun String.urlEncode(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
}

/**
 * Decode URL encoded string
 */
fun String.urlDecode(): String {
    return java.net.URLDecoder.decode(this, "UTF-8")
}

/**
 * Convert string to Base64
 */
fun String.toBase64(): String {
    return android.util.Base64.encodeToString(this.toByteArray(), android.util.Base64.DEFAULT)
}

/**
 * Decode Base64 string
 */
fun String.fromBase64(): String {
    return try {
        String(android.util.Base64.decode(this, android.util.Base64.DEFAULT))
    } catch (e: IllegalArgumentException) {
        ""
    }
}

/**
 * Check if string contains only digits
 */
fun String.isDigitsOnly(): Boolean {
    return this.isNotEmpty() && this.all { it.isDigit() }
}

/**
 * Reverse the string
 */
fun String.reverse(): String {
    return this.reversed()
}

/**
 * Count occurrences of substring
 */
fun String.countOccurrences(substring: String, ignoreCase: Boolean = false): Int {
    return this.split(substring, ignoreCase = ignoreCase).size - 1
}

/**
 * Check if string is a palindrome
 */
fun String.isPalindrome(ignoreCase: Boolean = true): Boolean {
    val cleaned = this.replace(Regex("[^A-Za-z0-9]"), "")
    val comparison = if (ignoreCase) cleaned.lowercase() else cleaned
    return comparison == comparison.reversed()
}

/**
 * Get words from string
 */
fun String.getWords(): List<String> {
    return this.split(Regex("\\s+")).filter { it.isNotEmpty() }
}

/**
 * Word count
 */
fun String.wordCount(): Int {
    return getWords().size
}

/**
 * Wrap text at specified line length
 */
fun String.wrap(lineLength: Int): String {
    val words = getWords()
    val result = StringBuilder()
    var currentLine = StringBuilder()
    
    for (word in words) {
        if (currentLine.length + word.length + 1 <= lineLength) {
            if (currentLine.isNotEmpty()) {
                currentLine.append(" ")
            }
            currentLine.append(word)
        } else {
            if (result.isNotEmpty()) {
                result.append("\n")
            }
            result.append(currentLine.toString())
            currentLine = StringBuilder(word)
        }
    }
    
    if (currentLine.isNotEmpty()) {
        if (result.isNotEmpty()) {
            result.append("\n")
        }
        result.append(currentLine.toString())
    }
    
    return result.toString()
}

/**
 * Format as file size (bytes to human readable)
 */
fun Long.formatAsFileSize(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

/**
 * Format timestamp as relative time (e.g., "2 hours ago")
 */
fun Long.formatAsRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 2592000_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(this))
    }
}