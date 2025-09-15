package com.fibreflow.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fibreflow.core.database.FibreFieldDatabase
import com.fibreflow.core.database.entities.DropEntity
import com.fibreflow.core.database.entities.DropStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DropDaoTest {

    private lateinit var database: FibreFieldDatabase
    private lateinit var dropDao: DropDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FibreFieldDatabase::class.java
        ).allowMainThreadQueries().build()

        dropDao = database.dropDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test insert and retrieve drop`() = runTest {
        // Given
        val drop = createTestDrop()

        // When
        dropDao.insert(drop)
        val retrieved = dropDao.getDropById(drop.id)

        // Then
        assertEquals(drop.id, retrieved?.id)
        assertEquals(drop.address, retrieved?.address)
        assertEquals(drop.status, retrieved?.status)
    }

    @Test
    fun `test update drop status`() = runTest {
        // Given
        val drop = createTestDrop()
        dropDao.insert(drop)

        // When
        dropDao.updateDropStatus(drop.id, DropStatus.IN_PROGRESS)
        val updated = dropDao.getDropById(drop.id)

        // Then
        assertEquals(DropStatus.IN_PROGRESS, updated?.status)
    }

    @Test
    fun `test delete drop`() = runTest {
        // Given
        val drop = createTestDrop()
        dropDao.insert(drop)

        // When
        dropDao.delete(drop)
        val retrieved = dropDao.getDropById(drop.id)

        // Then
        assertTrue(retrieved == null)
    }

    @Test
    fun `test get drops by status`() = runTest {
        // Given
        val drop1 = createTestDrop(status = DropStatus.AVAILABLE)
        val drop2 = createTestDrop(status = DropStatus.IN_PROGRESS)
        dropDao.insert(drop1)
        dropDao.insert(drop2)

        // When
        val availableDrops = dropDao.getDropsByStatus(DropStatus.AVAILABLE)

        // Then
        assertEquals(1, availableDrops.size)
        assertEquals(DropStatus.AVAILABLE, availableDrops[0].status)
    }

    @Test
    fun `test get drops needing sync`() = runTest {
        // Given
        val drop1 = createTestDrop(needsSync = true)
        val drop2 = createTestDrop(needsSync = false)
        dropDao.insert(drop1)
        dropDao.insert(drop2)

        // When
        val dropsNeedingSync = dropDao.getDropsNeedingSync()

        // Then
        assertEquals(1, dropsNeedingSync.size)
        assertTrue(dropsNeedingSync[0].needsSync)
    }

    private fun createTestDrop(
        id: Long = 1L,
        address: String = "123 Test Street",
        status: DropStatus = DropStatus.AVAILABLE,
        needsSync: Boolean = false
    ): DropEntity {
        return DropEntity(
            id = id,
            projectId = 1L,
            customerName = "Test Customer",
            address = address,
            latitude = -33.9249,
            longitude = 18.4241,
            status = status,
            estimatedInstallTime = 120,
            notes = "Test drop",
            needsSync = needsSync,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}