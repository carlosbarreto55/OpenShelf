package com.example.paperReader.build

import com.example.paperReader.BuildConfig
import java.io.File

/**
 * Reads the merged manifest AGP produced for the variant currently under test.
 *
 * The register's AND-001/AND-003 exit criteria are about what the *merged* manifest contains, not
 * about what a source manifest says, so these assertions read the real merged output rather than
 * an Android framework object that hides the attributes in question.
 */
object MergedManifest {

    fun forVariantUnderTest(): String {
        val variant = BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE.replaceFirstChar(Char::uppercase)
        return read(variant)
    }

    fun read(variant: String): String {
        val candidates = listOf(
            "build/intermediates/merged_manifests/$variant",
            "build/intermediates/merged_manifest/$variant",
            "build/intermediates/packaged_manifests/$variant",
        )
        val moduleDir = moduleDirectory()
        val manifest = candidates
            .map { File(moduleDir, it) }
            .filter(File::isDirectory)
            .firstNotNullOfOrNull { dir ->
                dir.walkTopDown().firstOrNull { it.name == "AndroidManifest.xml" }
            }
        return checkNotNull(manifest) {
            "No merged manifest for variant $variant under ${moduleDir.absolutePath}. " +
                "Assemble or process the variant's manifest before running this assertion."
        }.readText()
    }

    /** Unit tests run with the module directory as the working directory; walk up if that changes. */
    private fun moduleDirectory(): File {
        var candidate: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (candidate != null) {
            if (File(candidate, "build/intermediates").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }
}
