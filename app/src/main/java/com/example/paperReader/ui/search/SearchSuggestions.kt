package com.example.paperReader.ui.search

/**
 * Starting points for a user who has not searched yet.
 *
 * A fixed local list, owned by the presentation layer. It carries no counts, no ranking and no
 * claim that anything selected these: the client has no source for any of that, and deriving them
 * from fixture data would make demo content look like product content and would change the moment
 * a real backend landed (design.md decision 5).
 *
 * Each entry is submitted through exactly the same path as typed input, so it debounces, caches
 * and records into recent searches identically.
 */
val SUGGESTED_TOPICS: List<String> = listOf(
    "machine learning",
    "climate change",
    "genomics",
    "quantum computing",
    "public health",
    "materials science",
)
