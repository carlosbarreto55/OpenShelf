package com.example.paperReader.data.local

import com.example.paperReader.core.Clock

class FakeClock(var timeEpochMillis: Long = 0L) : Clock {
    override fun nowEpochMillis(): Long = timeEpochMillis
}
