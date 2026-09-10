package com.example.paperReader.contract

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reader for the pinned consumer-contract bundle under `src/test/resources/contract/<version>`.
 *
 * The bundle is the single place this repository states what it expects API v1 to mean. Fixtures
 * are checksum-pinned in `manifest.json` so a local edit cannot silently redefine server behavior;
 * see [ContractBundleIntegrityTest]. Regenerate the checksums only as part of a reviewed contract
 * update with `python3 tools/update-contract-manifest.py <bundle dir>`.
 */
object ContractBundle {
    const val VERSION_DIR = "contract/v1"

    /** Lenient on purpose: the manifest and fixtures are read as data, not as the production DTOs. */
    private val json = Json { ignoreUnknownKeys = true }

    val manifest: ContractManifest by lazy {
        json.decodeFromString(ContractManifest.serializer(), readText("manifest.json"))
    }

    /** Raw fixture bytes, exactly as the server would put them on the wire. */
    fun readText(relativePath: String): String {
        val resource = "/$VERSION_DIR/$relativePath"
        return checkNotNull(ContractBundle::class.java.getResourceAsStream(resource)) {
            "Missing pinned contract resource $resource"
        }.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun requestExpectation(name: String): ExpectedRequest {
        return json.decodeFromString(ExpectedRequest.serializer(), readText("requests/$name.json"))
    }

    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }
}

@Serializable
data class ContractManifest(
    val apiVersion: String,
    val contractVersion: String,
    val upstream: ContractUpstream,
    val resources: Map<String, String>,
)

@Serializable
data class ContractUpstream(
    val producer: String,
    val provenance: String,
    val verifiedByProviderCi: Boolean,
)

/** A recorded expectation about the HTTP request the client must produce. */
@Serializable
data class ExpectedRequest(
    val name: String,
    val description: String,
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    @SerialName("absent_query_parameters") val absentQueryParameters: List<String> = emptyList(),
)
