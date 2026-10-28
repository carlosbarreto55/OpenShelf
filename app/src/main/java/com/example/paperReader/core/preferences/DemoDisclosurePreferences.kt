package com.example.paperReader.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Whether the user has acknowledged the first-run explanation that this build runs on placeholder
 * data.
 *
 * A single boolean, deliberately not in Room: it is never queried or joined alongside paper data,
 * so storing it there would cost a schema version and a migration for one flag (design.md
 * decision 4). The interface keeps the ViewModel free of Android storage types and keeps the flag
 * fakeable in tests.
 *
 * Acknowledgement only ever suppresses the *explanation*. The compact indicator and the
 * at-the-point-of-consequence disclosure inside a simulated download are not dismissible.
 */
interface DemoDisclosurePreferences {
    /** Emits the stored acknowledgement and every later change to it. */
    val isAcknowledged: Flow<Boolean>

    suspend fun acknowledge()
}
