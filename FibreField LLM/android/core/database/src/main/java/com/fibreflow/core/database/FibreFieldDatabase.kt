package com.fibreflow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fibreflow.core.database.converters.DateConverters
import com.fibreflow.core.database.converters.LocationConverters
import com.fibreflow.core.database.converters.StatusConverters
import com.fibreflow.core.database.converters.JsonConverters
import com.fibreflow.core.database.dao.*
import com.fibreflow.core.database.entities.*

/**
 * Main Room database for FibreField application
 * Contains all entities and provides DAO access
 */
@Database(
    entities = [
        ProjectEntity::class,
        DropEntity::class,
        TechnicianEntity::class,
        InstallationEntity::class,
        PhotoEntity::class,
        RemediationEntity::class,
        SyncQueueEntity::class,
        AIConversationEntity::class,
        ValidationResultEntity::class,
        SessionEntity::class,
        OfflineMapTileEntity::class,
        ConfigurationEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(
    DateConverters::class,
    LocationConverters::class,
    StatusConverters::class,
    JsonConverters::class
)
abstract class FibreFieldDatabase : RoomDatabase() {

    // Project management
    abstract fun projectDao(): ProjectDao
    abstract fun dropDao(): DropDao
    abstract fun technicianDao(): TechnicianDao

    // Installation workflow
    abstract fun installationDao(): InstallationDao
    abstract fun photoDao(): PhotoDao

    // Issue management
    abstract fun remediationDao(): RemediationDao

    // Sync and offline
    abstract fun syncQueueDao(): SyncQueueDao

    // AI and validation
    abstract fun aiConversationDao(): AIConversationDao
    abstract fun validationResultDao(): ValidationResultDao

    // Session management
    abstract fun sessionDao(): SessionDao

    // Configuration
    abstract fun configurationDao(): ConfigurationDao

    companion object {
        const val DATABASE_NAME = "fibrefield.db"
    }
}