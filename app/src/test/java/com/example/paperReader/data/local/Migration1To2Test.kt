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
 * Regression test for the crash that motivated this migration: [DownloadEntity] gained
 * `sourceUrl`/`mimeType` without a version bump, so anyone with an existing v1 database hit
 * Room's identity-hash check. Builds the true v1 `downloads` table by hand (nine original
 * columns, no schema-export dependency) and asserts [MIGRATION_1_2] upgrades it in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration1To2Test {
    private val dbName = "migration-1-2-test.db"
    private lateinit var db: SupportSQLiteDatabase

    @After
    fun tearDown() {
        db.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(dbName)
    }

    @Test
    fun addsSourceUrlAndMimeTypeWithoutLosingExistingRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE downloads (
                                id TEXT NOT NULL PRIMARY KEY,
                                paperId TEXT NOT NULL,
                                title TEXT NOT NULL,
                                state TEXT NOT NULL,
                                bytesDownloaded INTEGER,
                                totalBytes INTEGER,
                                localContentUri TEXT,
                                errorMessage TEXT,
                                updatedAtEpochMillis INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO downloads
                (id, paperId, title, state, bytesDownloaded, totalBytes, localContentUri, errorMessage, updatedAtEpochMillis)
            VALUES ('d1', 'W1', 'Demo title', 'COMPLETED', 100, 100, 'content://demo/1', NULL, 0)
            """.trimIndent(),
        )

        MIGRATION_1_2.migrate(db)

        db.query("SELECT title, sourceUrl, mimeType FROM downloads WHERE id = 'd1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Demo title", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
    }
}
