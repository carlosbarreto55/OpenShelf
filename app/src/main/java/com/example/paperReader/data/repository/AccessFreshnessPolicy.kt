package com.example.paperReader.data.repository

/** How long a cached confirmed access snapshot (`DIRECT_PDF`/`OPEN_FULL_TEXT`) may be trusted
 * before [com.example.paperReader.data.local.LocalMappers] downgrades it to `Unknown` rather than
 * assuming continued availability. */
internal const val ACCESS_FRESHNESS_MILLIS = 24L * 60 * 60 * 1000
