package com.fibreflow.core.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converters for JSON data in Room database
 */
class JsonConverters {

    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): Map<String, Any>? {
        if (value.isNullOrBlank()) return null
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun mapToString(map: Map<String, Any>?): String? {
        return map?.let { gson.toJson(it) }
    }

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
    fun fromIntList(value: String?): List<Int>? {
        if (value.isNullOrBlank()) return null
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun intListToString(list: List<Int>?): String? {
        return list?.let { gson.toJson(it) }
    }
}