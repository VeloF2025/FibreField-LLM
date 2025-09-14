package com.fibreflow.core.database.converters

import androidx.room.TypeConverter
import java.util.*

/**
 * Type converters for Date objects in Room database
 */
class DateConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}