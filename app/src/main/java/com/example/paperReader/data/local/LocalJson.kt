package com.example.paperReader.data.local

import kotlinx.serialization.json.Json

/** Serialization used only for embedded Room columns (author/topic lists). Kept independent from
 * [com.example.paperReader.data.remote.RemoteJson] so the local persistence layer has no
 * compile-time dependency on the remote/backend layer. */
val LocalJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
