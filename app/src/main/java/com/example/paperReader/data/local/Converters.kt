package com.example.paperReader.data.local

import androidx.room.TypeConverter
import com.example.paperReader.data.local.entity.PersistedAuthor
import com.example.paperReader.data.local.entity.PersistedTopic
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class Converters {
    @TypeConverter
    fun paperTypeToString(value: PaperType): String = value.name

    @TypeConverter
    fun stringToPaperType(value: String): PaperType = PaperType.valueOf(value)

    @TypeConverter
    fun downloadStateToString(value: DownloadState): String = value.name

    @TypeConverter
    fun stringToDownloadState(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter
    fun authorsToJson(value: List<PersistedAuthor>): String = LocalJson.encodeToString(value)

    @TypeConverter
    fun jsonToAuthors(value: String): List<PersistedAuthor> = LocalJson.decodeFromString(value)

    @TypeConverter
    fun topicsToJson(value: List<PersistedTopic>): String = LocalJson.encodeToString(value)

    @TypeConverter
    fun jsonToTopics(value: String): List<PersistedTopic> = LocalJson.decodeFromString(value)
}
