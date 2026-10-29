package com.example.paperReader.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.entity.ReadingPositionEntity
import com.example.paperReader.domain.model.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [MIGRATION_3_4] adds `reading_positions` and touches nothing else, from every schema version this
 * repository exports below 4 — 2 and 3; version 1 predates export.
 *
 * Each case builds the old database by hand ([HistoricalSchemas]) and then opens it through Room
 * with the production migrations. Room validates every table against its entity when a migration
 * completes, so a migration that drifted from [ReadingPositionEntity] fails the open — the same
 * guarantee `MigrationTestHelper` gives against 4.json, which Robolectric cannot load.
 *
 * The rows seeded are what a user would lose sleep over: saved papers and download records.
 * Collections do not exist yet; when `add-library-collections` lands, its table joins this list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration3To4Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: PaperReaderDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun fromVersion3UserDataSurvivesAndPositionsStartEmpty() = runBlocking {
        createAt(version = 3, HistoricalSchemas.VERSION_3)

        openMigrated().assertUserDataSurvived()
    }

    @Test
    fun fromVersion2ThroughEveryLaterMigration() = runBlocking {
        createAt(version = 2, HistoricalSchemas.VERSION_2)

        openMigrated().assertUserDataSurvived()
    }

    @Test
    fun theMigratedTableStoresAndReadsAPosition() = runBlocking {
        createAt(version = 3, HistoricalSchemas.VERSION_3)
        val positions = openMigrated().readingPositionDao()

        positions.upsert(ReadingPositionEntity("W1", pageIndex = 4, pageOffsetFraction = 0.25f, updatedAtEpochMillis = 100))

        assertEquals(4, positions.get("W1")?.pageIndex)
        assertEquals(listOf("W1"), positions.observeResumable().first().map { it.paperId })
    }

    private fun createAt(version: Int, schema: List<String>) {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        schema.forEach(db::execSQL)
                        db.seedUserData()
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    /** The database exactly as the app builds it, so the migrations run are the ones that ship. */
    private fun openMigrated(): PaperReaderDatabase {
        return Room.databaseBuilder(context, PaperReaderDatabase::class.java, DB_NAME)
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
    }

    private fun SupportSQLiteDatabase.seedUserData() {
        execSQL(
            """
            INSERT INTO papers
                (id, title, authors, venue, publicationYear, type, topics, citationCount, abstractText, updatedAtEpochMillis)
            VALUES ('W1', 'Title W1', '[]', NULL, NULL, 'ARTICLE', '[]', NULL, NULL, 0)
            """.trimIndent(),
        )
        execSQL("INSERT INTO saved_papers (paperId, savedAtEpochMillis) VALUES ('W1', 500)")
        execSQL(
            """
            INSERT INTO downloads
                (id, paperId, title, state, sourceUrl, mimeType, bytesDownloaded, totalBytes,
                 localContentUri, errorMessage, updatedAtEpochMillis)
            VALUES ('W1', 'W1', 'Title W1', 'COMPLETED', 'https://cdn.example.org/W1.pdf',
                    'application/pdf', 10, 10, 'content://app/W1.pdf', NULL, 0)
            """.trimIndent(),
        )
    }

    private suspend fun PaperReaderDatabase.assertUserDataSurvived() {
        assertEquals("Papers survive", "Title W1", paperDao().get("W1")?.title)
        assertEquals("Saved state is user-owned", listOf("W1"), savedPaperDao().observeSaved().first().map { it.id })
        val download = downloadDao().get("W1")
        assertEquals("The completed download is still completed", DownloadState.COMPLETED, download?.state)
        assertEquals("…and still names its file", "content://app/W1.pdf", download?.localContentUri)
        assertNull("No position is invented for an existing download", readingPositionDao().get("W1"))
    }

    private companion object {
        const val DB_NAME = "migration-3-4-test.db"
    }
}
