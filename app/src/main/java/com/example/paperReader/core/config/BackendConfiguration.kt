package com.example.paperReader.core.config

import com.example.paperReader.BuildConfig

enum class BackendMode {
    FAKE,
    HTTP;

    companion object {
        fun from(value: String?): BackendMode {
            return entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: FAKE
        }
    }
}

data class BackendConfiguration(
    val mode: BackendMode,
    val baseUrl: String?,
) {
    val shouldUseHttp: Boolean
        get() = mode == BackendMode.HTTP && !baseUrl.isNullOrBlank()

    companion object {
        fun fromBuildConfig(): BackendConfiguration {
            val configuredBaseUrl = BuildConfig.PAPER_READER_BACKEND_BASE_URL
                .trim()
                .takeIf(String::isNotBlank)
            return BackendConfiguration(
                mode = BackendMode.from(BuildConfig.PAPER_READER_REMOTE_MODE),
                baseUrl = configuredBaseUrl,
            )
        }
    }
}
