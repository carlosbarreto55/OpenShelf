package com.example.paperReader.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.entity.PaperEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaperReaderDatabaseTest {
    private lateinit var database: PaperReaderDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        )
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun sanityCheckInsertAndRead() = runTest {
        database.paperDao().upsertAll(listOf(TestFixtures.paperEntity("W1")))

        assertEquals("Demo title", database.paperDao().get("W1")?.title)
    }
}
