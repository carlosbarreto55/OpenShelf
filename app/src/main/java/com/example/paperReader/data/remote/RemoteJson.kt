package com.example.paperReader.data.remote

import kotlinx.serialization.json.Json

val RemoteJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
