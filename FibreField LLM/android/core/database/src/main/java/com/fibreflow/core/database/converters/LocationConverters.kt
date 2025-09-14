package com.fibreflow.core.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converters for location/GPS data in Room database
 */
class LocationConverters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun stringListToString(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun fromLongList(value: String?): List<Long>? {
        if (value.isNullOrBlank()) return null
        val type = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun longListToString(list: List<Long>?): String? {
        return list?.let { gson.toJson(it) }
    }
}