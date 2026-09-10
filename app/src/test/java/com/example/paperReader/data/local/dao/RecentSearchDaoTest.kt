package com.example.paperReader.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.first
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
class RecentSearchDaoTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var dao: RecentSearchDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.recentSearchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trimToMostRecentKeepsOnlyTheNewestEntries() = runTest {
        repeat(5) { index ->
            dao.upsert(RecentSearchEntity(queryText = "query-$index", lastUsedAtEpochMillis = index.toLong()))
        }

        dao.trimToMostRecent(2)

        val remaining = dao.observeRecent(limit = 10).first().map { it.queryText }
        assertEquals(listOf("query-4", "query-3"), remaining)
    }

    @Test
    fun clearAllRemovesEveryRecentQuery() = runTest {
        dao.upsert(RecentSearchEntity(queryText = "AI", lastUsedAtEpochMillis = 0L))

        dao.clearAll()

        assertEquals(emptyList<String>(), dao.observeRecent(limit = 10).first())
    }
}
