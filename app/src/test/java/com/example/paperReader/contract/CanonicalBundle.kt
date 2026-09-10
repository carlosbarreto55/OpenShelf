package com.example.paperReader.contract

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Reader for the **provider-published** contract bundle vendored under
 * `src/test/resources/contract/canonical`.
 *
 * This is a byte-exact copy of `PaperReaderApi/contracts/v1`, carrying its own `SHA256SUMS`. It is
 * the authoritative definition of v1 (SHR-002); the consumer bundle under [ContractBundle] states
 * this repository's *expectations* and is checked against it.
 *
 * Nothing here may be edited by hand. Re-vendor the whole directory from the provider release and
 * let [CanonicalContractTest] re-verify the published digests.
 */
object CanonicalBundle {
    const val DIR = "contract/canonical"

    /** Lenient on purpose: fixtures are read as data, not as the production DTOs. */
    private val json = Json { ignoreUnknownKeys = true }

    /** The provider's release identifier, e.g. `paperreader-v1.0.0`. */
    val version: String by lazy { readText("VERSION").trim() }

    /** The published digests, as `relative path -> lowercase hex sha256`. */
    val publishedChecksums: Map<String, String> by lazy {
        readText("SHA256SUMS").lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .associate { line ->
                // `<hex>  <path>` — two spaces, the coreutils/shasum format.
                val digest = line.substringBefore(' ').trim()
                val path = line.substringAfter(' ').trim()
                path to digest
            }
    }

    private val fixtureFile: CanonicalFixtures by lazy {
        json.decodeFromString(
            CanonicalFixtures.serializer(),
            readText("fixtures/canonical-fixtures.json"),
        )
    }

    /** The version the fixture file itself declares, which must agree with [version]. */
    val fixturesContractVersion: String get() = fixtureFile.contractVersion

    val fixtures: List<CanonicalFixture> get() = fixtureFile.fixtures

    fun fixture(name: String): CanonicalFixture {
        return checkNotNull(fixtures.firstOrNull { it.name == name }) {
            "No canonical fixture named '$name'. Available: ${fixtures.map(CanonicalFixture::name)}"
        }
    }

    /** The fixture's `response` re-encoded exactly as the server would put it on the wire. */
    fun responseBody(name: String): String = json.encodeToString(JsonElement.serializer(), fixture(name).response)

    fun readText(relativePath: String): String = readBytes(relativePath).toString(Charsets.UTF_8)

    /** Raw bytes, so a digest is taken over the published file and not a re-encoding of it. */
    fun readBytes(relativePath: String): ByteArray {
        val resource = "/$DIR/$relativePath"
        return checkNotNull(CanonicalBundle::class.java.getResourceAsStream(resource)) {
            "Missing canonical contract resource $resource"
        }.use { it.readBytes() }
    }

    fun sha256(content: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it) }
    }
}

@Serializable
data class CanonicalFixtures(
    @SerialName("contract_version") val contractVersion: String,
    val fixtures: List<CanonicalFixture>,
)

/** One provider-authored request/response pair. */
@Serializable
data class CanonicalFixture(
    val name: String,
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val status: Int,
    val response: JsonElement,
)
