package com.example.paperReader.core.config

import com.example.paperReader.BuildConfig
import java.net.URI

enum class BackendMode {
    /** Deterministic fixture data. The demo flavor's only mode; needs no backend and no network. */
    FAKE,

    /** Live PaperReader backend over Retrofit. The only mode of the integration and production
     * flavors; they differ in where the base URL comes from, not in how it is used. */
    HTTP,
    ;

    companion object {
        /** Null rather than a fallback: an unrecognized mode is a build defect, and quietly
         * choosing FAKE is exactly the silent-demo-data failure AND-002 records. */
        fun parse(value: String?): BackendMode? {
            return entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
        }
    }
}

enum class BackendConfigurationProblem {
    UNKNOWN_MODE,
    MISSING_BASE_URL,
    MALFORMED_BASE_URL,
    CLEARTEXT_BASE_URL_IN_RELEASE,
    CLEARTEXT_BASE_URL_FOR_REMOTE_HOST,
}

/**
 * The resolved backend mode for the running build.
 *
 * The mode is fixed by the product flavor, never inferred from whether configuration happens to be
 * complete. An HTTP build with a missing or unusable base URL fails loudly instead of substituting
 * fixture data — see design.md decision 1 and TECHNICAL_DEBT.md AND-002.
 */
data class BackendConfiguration(
    val mode: BackendMode,
    val baseUrl: String?,
) {
    val usesHttp: Boolean get() = mode == BackendMode.HTTP

    /** Present exactly when [usesHttp]; validated by [resolve] before this type is constructed. */
    fun requireBaseUrl(): String = checkNotNull(baseUrl) { "An HTTP backend configuration always has a base URL" }

    companion object {
        /** Cleartext is a local-development affordance only, and only for the hosts the
         * integrationDebug network-security config also allows. */
        val LOOPBACK_DEVELOPMENT_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

        /** The unresolved outcome for this build, for callers that must observe a configuration
         * problem without turning it into a crash themselves. */
        fun resultFromBuildConfig(): BackendConfigurationResult = resolve(
            rawMode = BuildConfig.PAPER_READER_REMOTE_MODE,
            rawBaseUrl = BuildConfig.PAPER_READER_BACKEND_BASE_URL,
            isDebugBuild = BuildConfig.DEBUG,
        )

        /** Throws when the build is misconfigured, so a broken HTTP build fails as soon as
         * anything needs a backend rather than running against demo data. */
        fun fromBuildConfig(): BackendConfiguration {
            return when (val result = resultFromBuildConfig()) {
                is BackendConfigurationResult.Valid -> result.configuration
                is BackendConfigurationResult.Invalid -> throw IllegalStateException(result.message)
            }
        }

        fun resolve(
            rawMode: String?,
            rawBaseUrl: String?,
            isDebugBuild: Boolean,
        ): BackendConfigurationResult {
            val mode = BackendMode.parse(rawMode)
                ?: return invalid(
                    BackendConfigurationProblem.UNKNOWN_MODE,
                    "Unknown backend mode \"$rawMode\". Build the demo, integration or production flavor.",
                )
            if (mode == BackendMode.FAKE) {
                return BackendConfigurationResult.Valid(BackendConfiguration(BackendMode.FAKE, baseUrl = null))
            }

            val baseUrl = rawBaseUrl?.trim().orEmpty()
            if (baseUrl.isEmpty()) {
                return invalid(
                    BackendConfigurationProblem.MISSING_BASE_URL,
                    "This build has no PaperReader backend base URL. Pass " +
                        "-PpaperReaderBackendBaseUrl=<url>; an HTTP build never falls back to demo data.",
                )
            }

            val uri = runCatching { URI(baseUrl) }.getOrNull()
            val scheme = uri?.scheme?.lowercase()
            if (uri == null || uri.host.isNullOrBlank() || (scheme != "http" && scheme != "https")) {
                return invalid(
                    BackendConfigurationProblem.MALFORMED_BASE_URL,
                    "\"$baseUrl\" is not a usable http(s) PaperReader backend base URL.",
                )
            }
            if (scheme == "http" && !isDebugBuild) {
                return invalid(
                    BackendConfigurationProblem.CLEARTEXT_BASE_URL_IN_RELEASE,
                    "\"$baseUrl\" is cleartext. A release build must use https.",
                )
            }
            if (scheme == "http" && uri.host !in LOOPBACK_DEVELOPMENT_HOSTS) {
                return invalid(
                    BackendConfigurationProblem.CLEARTEXT_BASE_URL_FOR_REMOTE_HOST,
                    "\"$baseUrl\" is cleartext for a host outside the debug loopback allowlist " +
                        "${LOOPBACK_DEVELOPMENT_HOSTS.sorted()}. Use https for any other host.",
                )
            }

            return BackendConfigurationResult.Valid(
                BackendConfiguration(BackendMode.HTTP, baseUrl = baseUrl.ensureTrailingSlash()),
            )
        }

        private fun invalid(problem: BackendConfigurationProblem, message: String) =
            BackendConfigurationResult.Invalid(problem, message)

        private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
    }
}

sealed interface BackendConfigurationResult {
    data class Valid(val configuration: BackendConfiguration) : BackendConfigurationResult

    data class Invalid(
        val problem: BackendConfigurationProblem,
        val message: String,
    ) : BackendConfigurationResult
}
