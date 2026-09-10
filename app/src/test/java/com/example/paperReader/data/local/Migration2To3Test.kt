package com.example.paperReader.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AND-009's migration half: a database that predates the unique `(queryKey, paperId)` index can
 * already contain duplicate associations, so [MIGRATION_2_3] has to make the data satisfy the
 * index before creating it — keeping each paper's *lowest* position, which is where it first
 * appeared in backend order.
 *
 * The other assertion here matters just as much: nothing the user owns may be collateral damage.
 * Papers, saved state, access snapshots and download records must come through untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration2To3Test {
    private lateinit var db: SupportSQLiteDatabase

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        context().deleteDatabase(DB_NAME)
    }

    @Test
    fun duplicateAssociationsCollapseToTheirLowestPositionAndUserDataSurvives() {
        openVersion2().let { db ->
            db.seedPapers()
            db.seedQuery("q1")
            // "q1" saw W1 at positions 0 and 2 and W2 at 1 — exactly the overlap the old
            // (queryKey, position) identity allowed.
            db.seedAssociation("q1", position = 0, paperId = "W1")
            db.seedAssociation("q1", position = 1, paperId = "W2")
            db.seedAssociation("q1", position = 2, paperId = "W1")
            db.seedAssociation("q1", position = 3, paperId = "W3")

            db.seedQuery("q2")
            db.seedAssociation("q2", position = 0, paperId = "W2")
            db.seedAssociation("q2", position = 1, paperId = "W2")

            db.seedSavedPaper("W1")
            db.seedAccessSnapshot("W1")
            db.seedDownload("W1")
        }

        MIGRATION_2_3.migrate(db)

        db.let { db ->
            assertEquals(
                "W1 keeps position 0, its first-seen place; the later copy is dropped",
                listOf("W1" to 0, "W2" to 1, "W3" to 3),
                db.associations("q1"),
            )
            assertEquals(listOf("W2" to 0), db.associations("q2"))

            assertEquals("Papers are never deleted by this migration", 3, db.count("papers"))
            assertEquals("Saved state is user-owned", 1, db.count("saved_papers"))
            assertEquals("Access snapshots survive", 1, db.count("access_snapshots"))
            assertEquals("Download records survive", 1, db.count("downloads"))

            assertTrue("The unique index must exist after migrating", db.hasUniqueQueryPaperIndex())
        }
    }

    @Test
    fun aDatabaseWithNoDuplicatesIsUnchangedApartFromTheIndex() {
        openVersion2().let { db ->
            db.seedPapers()
            db.seedQuery("q1")
            db.seedAssociation("q1", position = 0, paperId = "W1")
            db.seedAssociation("q1", position = 1, paperId = "W2")
        }

        MIGRATION_2_3.migrate(db)

        assertEquals(listOf("W1" to 0, "W2" to 1), db.associations("q1"))
    }

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * Builds the v2 tables by hand rather than through `MigrationTestHelper`, matching
     * [Migration1To2Test]: the exported schema assets are not on the JVM unit-test classpath, and
     * a literal `CREATE TABLE` is in any case the clearest statement of what shipped at v2.
     */
    private fun openVersion2(): SupportSQLiteDatabase {
        val context = context()
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        VERSION_2_SCHEMA.forEach(db::execSQL)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
        return db
    }

    private fun SupportSQLiteDatabase.seedPapers() {
        listOf("W1", "W2", "W3").forEach { id ->
            execSQL(
                """
                INSERT INTO papers
                    (id, title, authors, venue, publicationYear, type, topics, citationCount, abstractText, updatedAtEpochMillis)
                VALUES ('$id', 'Title $id', '[]', NULL, NULL, 'ARTICLE', '[]', NULL, NULL, 0)
                """.trimIndent(),
            )
        }
    }

    private fun SupportSQLiteDatabase.seedQuery(key: String) {
        execSQL(
            """
            INSERT INTO search_queries
                (queryKey, queryText, sortOrder, openAccessOnly, pdfAvailableOnly, publicationYear,
                 workType, topic, nextCursor, totalCount, fetchedAtEpochMillis)
            VALUES ('$key', 'ai', 'relevance', 0, 0, NULL, NULL, NULL, NULL, NULL, 0)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.seedAssociation(queryKey: String, position: Int, paperId: String) {
        execSQL("INSERT INTO search_results (queryKey, position, paperId) VALUES ('$queryKey', $position, '$paperId')")
    }

    private fun SupportSQLiteDatabase.seedSavedPaper(paperId: String) {
        execSQL("INSERT INTO saved_papers (paperId, savedAtEpochMillis) VALUES ('$paperId', 500)")
    }

    private fun SupportSQLiteDatabase.seedAccessSnapshot(paperId: String) {
        execSQL(
            """
            INSERT INTO access_snapshots
                (paperId, state, sourceUrl, descriptorUrl, descriptorMimeType, descriptorProvenance,
                 descriptorGenerated, descriptorExpiresAtEpochMillis, provenance, checkedAtEpochMillis)
            VALUES ('$paperId', 'DIRECT_PDF', NULL, 'https://cdn.example.org/$paperId.pdf',
                    'application/pdf', NULL, 0, NULL, NULL, 0)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.seedDownload(paperId: String) {
        execSQL(
            """
            INSERT INTO downloads
                (id, paperId, title, state, sourceUrl, mimeType, bytesDownloaded, totalBytes,
                 localContentUri, errorMessage, updatedAtEpochMillis)
            VALUES ('$paperId', '$paperId', 'Title $paperId', 'COMPLETED',
                    'https://cdn.example.org/$paperId.pdf', 'application/pdf', 10, 10,
                    'content://demo/$paperId', NULL, 0)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.associations(queryKey: String): List<Pair<String, Int>> {
        return query("SELECT paperId, position FROM search_results WHERE queryKey = '$queryKey' ORDER BY position")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
                }
            }
    }

    private fun SupportSQLiteDatabase.count(table: String): Int {
        return query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    }

    private fun SupportSQLiteDatabase.hasUniqueQueryPaperIndex(): Boolean {
        return query("PRAGMA index_list('search_results')").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "index_search_results_queryKey_paperId" &&
                    cursor.getInt(uniqueColumn) == 1
                ) {
                    found = true
                }
            }
            found
        }
    }

    private companion object {
        const val DB_NAME = "migration-2-3-test.db"

        /** The tables as they existed at schema version 2, before the unique index. */
        val VERSION_2_SCHEMA = listOf(
            """
            CREATE TABLE papers (
                id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, authors TEXT NOT NULL,
                venue TEXT, publicationYear INTEGER, type TEXT NOT NULL, topics TEXT NOT NULL,
                citationCount INTEGER, abstractText TEXT, updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE access_snapshots (
                paperId TEXT NOT NULL PRIMARY KEY, state TEXT, sourceUrl TEXT, descriptorUrl TEXT,
                descriptorMimeType TEXT, descriptorProvenance TEXT,
                descriptorGenerated INTEGER NOT NULL, descriptorExpiresAtEpochMillis INTEGER,
                provenance TEXT, checkedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(paperId) REFERENCES papers(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE search_queries (
                queryKey TEXT NOT NULL PRIMARY KEY, queryText TEXT NOT NULL, sortOrder TEXT NOT NULL,
                openAccessOnly INTEGER NOT NULL, pdfAvailableOnly INTEGER NOT NULL,
                publicationYear INTEGER, workType TEXT, topic TEXT, nextCursor TEXT,
                totalCount INTEGER, fetchedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE search_results (
                queryKey TEXT NOT NULL, position INTEGER NOT NULL, paperId TEXT NOT NULL,
                PRIMARY KEY(queryKey, position),
                FOREIGN KEY(queryKey) REFERENCES search_queries(queryKey) ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX index_search_results_paperId ON search_results (paperId)",
            "CREATE INDEX index_search_results_queryKey ON search_results (queryKey)",
            "CREATE TABLE recent_searches (queryText TEXT NOT NULL PRIMARY KEY, lastUsedAtEpochMillis INTEGER NOT NULL)",
            "CREATE TABLE saved_papers (paperId TEXT NOT NULL PRIMARY KEY, savedAtEpochMillis INTEGER NOT NULL)",
            """
            CREATE TABLE downloads (
                id TEXT NOT NULL PRIMARY KEY, paperId TEXT NOT NULL, title TEXT NOT NULL,
                state TEXT NOT NULL, sourceUrl TEXT NOT NULL, mimeType TEXT,
                bytesDownloaded INTEGER, totalBytes INTEGER, localContentUri TEXT,
                errorMessage TEXT, updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
