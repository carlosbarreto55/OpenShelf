package com.example.paperReader.core

/** Injectable wall-clock so cache timestamps (fetchedAt/checkedAt/savedAt) are deterministic in tests. */
fun interface Clock {
    fun nowEpochMillis(): Long
}

object SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
