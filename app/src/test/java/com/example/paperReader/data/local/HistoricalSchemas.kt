package com.example.paperReader.data.local

/**
 * The tables as they shipped at earlier schema versions, written out as the `CREATE` statements
 * Room generated for them. The exported schema JSON is not on the Robolectric asset path, so
 * `MigrationTestHelper` cannot load it here; a migration test builds the old database from these
 * instead and lets Room's own open-time validation judge the result.
 */
object HistoricalSchemas {
    /** The tables as they existed at schema version 2, before the unique index. */
    val VERSION_2: List<String> = listOf(
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

    /** Version 3 added only the unique `(queryKey, paperId)` index. */
    val VERSION_3: List<String> = VERSION_2 +
        "CREATE UNIQUE INDEX index_search_results_queryKey_paperId ON search_results (queryKey, paperId)"
}
