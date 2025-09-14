// 🟢 WORKING: Date and time utility functions for consistent formatting and operations
package com.fibreflow.core.common.utils

import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Comprehensive date and time utilities for the FibreField application
 */
object DateTimeUtils {
    
    // Standard date formats used throughout the app
    const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    const val DISPLAY_DATE_FORMAT = "MMM dd, yyyy"
    const val DISPLAY_TIME_FORMAT = "HH:mm"
    const val DISPLAY_DATETIME_FORMAT = "MMM dd, yyyy HH:mm"
    const val FILE_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
    const val API_DATE_FORMAT = "yyyy-MM-dd"
    const val API_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
    
    // Time zone constants
    val UTC_TIMEZONE: TimeZone = TimeZone.getTimeZone("UTC")
    val SOUTH_AFRICA_TIMEZONE: TimeZone = TimeZone.getTimeZone("Africa/Johannesburg")
    
    /**
     * Get current timestamp in milliseconds
     */
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    
    /**
     * Get current timestamp as Instant
     */
    fun currentInstant(): Instant = Instant.now()
    
    /**
     * Get current date as LocalDate
     */
    fun currentDate(): LocalDate = LocalDate.now()
    
    /**
     * Get current time as LocalTime
     */
    fun currentTime(): LocalTime = LocalTime.now()
    
    /**
     * Get current date and time as LocalDateTime
     */
    fun currentDateTime(): LocalDateTime = LocalDateTime.now()
    
    /**
     * Convert timestamp to formatted string
     */
    fun formatTimestamp(
        timestampMillis: Long,
        format: String = DISPLAY_DATETIME_FORMAT,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val sdf = SimpleDateFormat(format, Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(timestampMillis))
    }
    
    /**
     * Convert Instant to formatted string
     */
    fun formatInstant(
        instant: Instant,
        format: String = DISPLAY_DATETIME_FORMAT,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val formatter = DateTimeFormatter.ofPattern(format)
        return instant.atZone(zoneId).format(formatter)
    }
    
    /**
     * Parse date string to timestamp
     */
    fun parseToTimestamp(
        dateString: String,
        format: String = ISO_8601_FORMAT,
        timeZone: TimeZone = UTC_TIMEZONE
    ): Long? {
        return try {
            val sdf = SimpleDateFormat(format, Locale.getDefault()).apply {
                this.timeZone = timeZone
            }
            sdf.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse date string to Instant
     */
    fun parseToInstant(
        dateString: String,
        format: String = ISO_8601_FORMAT
    ): Instant? {
        return try {
            when (format) {
                ISO_8601_FORMAT -> Instant.parse(dateString)
                else -> {
                    val formatter = DateTimeFormatter.ofPattern(format)
                    LocalDateTime.parse(dateString, formatter).atZone(ZoneId.systemDefault()).toInstant()
                }
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }
    
    /**
     * Convert LocalDateTime to timestamp
     */
    fun toTimestamp(localDateTime: LocalDateTime, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return localDateTime.atZone(zoneId).toInstant().toEpochMilli()
    }
    
    /**
     * Convert timestamp to LocalDateTime
     */
    fun toLocalDateTime(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), zoneId)
    }
    
    /**
     * Get start of day for given date
     */
    fun startOfDay(date: LocalDate): LocalDateTime {
        return date.atStartOfDay()
    }
    
    /**
     * Get end of day for given date
     */
    fun endOfDay(date: LocalDate): LocalDateTime {
        return date.atTime(23, 59, 59, 999_999_999)
    }
    
    /**
     * Calculate duration between two timestamps
     */
    fun durationBetween(startMillis: Long, endMillis: Long): Duration {
        return Duration.ofMillis(endMillis - startMillis)
    }
    
    /**
     * Calculate duration between two Instants
     */
    fun durationBetween(start: Instant, end: Instant): Duration {
        return Duration.between(start, end)
    }
    
    /**
     * Format duration as human readable string
     */
    fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    /**
     * Format duration as work time (e.g., "2:30:45")
     */
    fun formatWorkTime(duration: Duration): String {
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
    
    /**
     * Get relative time string (e.g., "2 hours ago", "in 3 days")
     */
    fun getRelativeTimeString(timestampMillis: Long): String {
        val now = currentTimeMillis()
        val diff = now - timestampMillis
        val absDiff = kotlin.math.abs(diff)
        
        val (value, unit, isPast) = when {
            absDiff < TimeUnit.MINUTES.toMillis(1) -> Triple(0, "minute", diff > 0)
            absDiff < TimeUnit.HOURS.toMillis(1) -> {
                Triple(TimeUnit.MILLISECONDS.toMinutes(absDiff), "minute", diff > 0)
            }
            absDiff < TimeUnit.DAYS.toMillis(1) -> {
                Triple(TimeUnit.MILLISECONDS.toHours(absDiff), "hour", diff > 0)
            }
            absDiff < TimeUnit.DAYS.toMillis(7) -> {
                Triple(TimeUnit.MILLISECONDS.toDays(absDiff), "day", diff > 0)
            }
            absDiff < TimeUnit.DAYS.toMillis(30) -> {
                Triple(TimeUnit.MILLISECONDS.toDays(absDiff) / 7, "week", diff > 0)
            }
            absDiff < TimeUnit.DAYS.toMillis(365) -> {
                Triple(TimeUnit.MILLISECONDS.toDays(absDiff) / 30, "month", diff > 0)
            }
            else -> {
                Triple(TimeUnit.MILLISECONDS.toDays(absDiff) / 365, "year", diff > 0)
            }
        }
        
        val plural = if (value == 1L) "" else "s"
        
        return when {
            value == 0L -> "Just now"
            isPast -> "$value $unit$plural ago"
            else -> "In $value $unit$plural"
        }
    }
    
    /**
     * Check if timestamp is today
     */
    fun isToday(timestampMillis: Long): Boolean {
        val date = toLocalDateTime(timestampMillis).toLocalDate()
        return date == currentDate()
    }
    
    /**
     * Check if timestamp is yesterday
     */
    fun isYesterday(timestampMillis: Long): Boolean {
        val date = toLocalDateTime(timestampMillis).toLocalDate()
        return date == currentDate().minusDays(1)
    }
    
    /**
     * Check if timestamp is this week
     */
    fun isThisWeek(timestampMillis: Long): Boolean {
        val date = toLocalDateTime(timestampMillis).toLocalDate()
        val now = currentDate()
        val startOfWeek = now.minusDays(now.dayOfWeek.value - 1L)
        val endOfWeek = startOfWeek.plusDays(6)
        
        return !date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)
    }
    
    /**
     * Get business days between two dates (excluding weekends)
     */
    fun getBusinessDaysBetween(start: LocalDate, end: LocalDate): Long {
        var businessDays = 0L
        var current = start
        
        while (!current.isAfter(end)) {
            if (current.dayOfWeek.value < 6) { // Monday = 1, Sunday = 7
                businessDays++
            }
            current = current.plusDays(1)
        }
        
        return businessDays
    }
    
    /**
     * Check if date is a business day (Monday-Friday)
     */
    fun isBusinessDay(date: LocalDate): Boolean {
        return date.dayOfWeek.value < 6
    }
    
    /**
     * Get next business day
     */
    fun getNextBusinessDay(date: LocalDate): LocalDate {
        var nextDay = date.plusDays(1)
        while (!isBusinessDay(nextDay)) {
            nextDay = nextDay.plusDays(1)
        }
        return nextDay
    }
    
    /**
     * Format timestamp for file naming
     */
    fun formatForFilename(timestampMillis: Long = currentTimeMillis()): String {
        return formatTimestamp(timestampMillis, FILE_TIMESTAMP_FORMAT, UTC_TIMEZONE)
    }
    
    /**
     * Format timestamp for API requests
     */
    fun formatForApi(timestampMillis: Long): String {
        return formatTimestamp(timestampMillis, ISO_8601_FORMAT, UTC_TIMEZONE)
    }
    
    /**
     * Convert South African time to UTC
     */
    fun saTimeToUtc(timestampMillis: Long): Long {
        val saCalendar = Calendar.getInstance(SOUTH_AFRICA_TIMEZONE).apply {
            timeInMillis = timestampMillis
        }
        
        val utcCalendar = Calendar.getInstance(UTC_TIMEZONE).apply {
            set(Calendar.YEAR, saCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, saCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, saCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, saCalendar.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, saCalendar.get(Calendar.MINUTE))
            set(Calendar.SECOND, saCalendar.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, saCalendar.get(Calendar.MILLISECOND))
        }
        
        return utcCalendar.timeInMillis
    }
    
    /**
     * Convert UTC time to South African time
     */
    fun utcToSaTime(timestampMillis: Long): Long {
        val utcCalendar = Calendar.getInstance(UTC_TIMEZONE).apply {
            timeInMillis = timestampMillis
        }
        
        val saCalendar = Calendar.getInstance(SOUTH_AFRICA_TIMEZONE).apply {
            set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, utcCalendar.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, utcCalendar.get(Calendar.MINUTE))
            set(Calendar.SECOND, utcCalendar.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, utcCalendar.get(Calendar.MILLISECOND))
        }
        
        return saCalendar.timeInMillis
    }
    
    /**
     * Get time zone offset for South Africa
     */
    fun getSouthAfricaOffset(): Long {
        return SOUTH_AFRICA_TIMEZONE.getOffset(currentTimeMillis()).toLong()
    }
    
    /**
     * Check if given time is within working hours (8 AM - 6 PM)
     */
    fun isWithinWorkingHours(timestampMillis: Long): Boolean {
        val saTime = utcToSaTime(timestampMillis)
        val hour = toLocalDateTime(saTime).hour
        return hour in 8..17 // 8 AM to 5 PM (inclusive)
    }
    
    /**
     * Get working hours start time for given date
     */
    fun getWorkingHoursStart(date: LocalDate): LocalDateTime {
        return date.atTime(8, 0)
    }
    
    /**
     * Get working hours end time for given date
     */
    fun getWorkingHoursEnd(date: LocalDate): LocalDateTime {
        return date.atTime(17, 0)
    }
    
    /**
     * Calculate working hours between two timestamps
     */
    fun calculateWorkingHours(startMillis: Long, endMillis: Long): Duration {
        val start = toLocalDateTime(startMillis)
        val end = toLocalDateTime(endMillis)
        
        var workingMinutes = 0L
        var current = start.toLocalDate()
        val endDate = end.toLocalDate()
        
        while (!current.isAfter(endDate)) {
            if (isBusinessDay(current)) {
                val dayStart = if (current == start.toLocalDate()) start else getWorkingHoursStart(current)
                val dayEnd = if (current == end.toLocalDate()) end else getWorkingHoursEnd(current)
                
                if (dayStart.isBefore(dayEnd)) {
                    val workStart = maxOf(dayStart, getWorkingHoursStart(current))
                    val workEnd = minOf(dayEnd, getWorkingHoursEnd(current))
                    
                    if (workStart.isBefore(workEnd)) {
                        workingMinutes += Duration.between(workStart, workEnd).toMinutes()
                    }
                }
            }
            current = current.plusDays(1)
        }
        
        return Duration.ofMinutes(workingMinutes)
    }
    
    /**
     * Get start of current month
     */
    fun startOfCurrentMonth(): LocalDateTime {
        return currentDate().withDayOfMonth(1).atStartOfDay()
    }
    
    /**
     * Get end of current month
     */
    fun endOfCurrentMonth(): LocalDateTime {
        return currentDate().withDayOfMonth(currentDate().lengthOfMonth()).atTime(23, 59, 59)
    }
    
    /**
     * Get age in years from birth date
     */
    fun calculateAge(birthDate: LocalDate): Int {
        return Period.between(birthDate, currentDate()).years
    }
}

/**
 * Extension functions for common date operations
 */

/**
 * Convert Long timestamp to formatted string
 */
fun Long.formatAsDateTime(format: String = DateTimeUtils.DISPLAY_DATETIME_FORMAT): String {
    return DateTimeUtils.formatTimestamp(this, format)
}

/**
 * Convert Long timestamp to relative time string
 */
fun Long.formatAsRelativeTime(): String {
    return DateTimeUtils.getRelativeTimeString(this)
}

/**
 * Check if Long timestamp is today
 */
fun Long.isToday(): Boolean {
    return DateTimeUtils.isToday(this)
}

/**
 * Convert LocalDateTime to timestamp
 */
fun LocalDateTime.toTimestamp(): Long {
    return DateTimeUtils.toTimestamp(this)
}

/**
 * Format LocalDateTime
 */
fun LocalDateTime.format(format: String = DateTimeUtils.DISPLAY_DATETIME_FORMAT): String {
    return this.format(DateTimeFormatter.ofPattern(format))
}

/**
 * Check if LocalDate is business day
 */
fun LocalDate.isBusinessDay(): Boolean {
    return DateTimeUtils.isBusinessDay(this)
}