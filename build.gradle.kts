// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
}

// See specs/backend-ready-data-layer/spec.md "Secret-free Android client": the OpenAlex API key
// belongs to the future backend only and must never be committed to this repository. These two
// tasks are the enforcement mechanism for that rule rather than a one-time manual check.
val secretScanExcludedDirNames = setOf(".git", ".gradle", ".idea", "build", "schemas", ".kotlin")
val secretScanPatterns = listOf(
    "OpenAlex API host referenced directly" to Regex("openalex\\.org", RegexOption.IGNORE_CASE),
    "OpenAlex API key identifier" to Regex("openalex[_-]?api[_-]?key", RegexOption.IGNORE_CASE),
    "hardcoded api_key parameter" to Regex("api_key\\s*=\\s*[\"'][^\"'\\s]{6,}[\"']", RegexOption.IGNORE_CASE),
)

tasks.register("checkNoOpenAlexSecret") {
    group = "verification"
    description = "Fails the build if an OpenAlex API host or key-shaped literal is committed " +
        "anywhere in the repository (source, resources, build configuration, fixtures, or logs)."
    val repoRoot = rootProject.projectDir
    val selfRelativePath = "build.gradle.kts"
    doLast {
        val offenders = mutableListOf<String>()
        repoRoot.walkTopDown()
            .onEnter { it.name !in secretScanExcludedDirNames }
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(repoRoot).path
                if (relativePath == selfRelativePath) return@forEach
                val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
                secretScanPatterns.forEach { (label, pattern) ->
                    if (pattern.containsMatchIn(text)) offenders += "$relativePath ($label)"
                }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Potential OpenAlex secret found — the Android client must only talk to the " +
                    "PaperReader backend, never OpenAlex directly:\n" +
                    offenders.joinToString("\n") { "  - $it" },
            )
        }
    }
}

tasks.register("checkNoOpenAlexSecretInArtifacts") {
    group = "verification"
    description = "Scans any already-built APK/AAB under build/outputs for an embedded OpenAlex " +
        "host or key-shaped literal; run :app:assembleDebug first to exercise a packaged artifact."
    doLast {
        val artifacts = rootProject.allprojects.flatMap { project ->
            val outputsDir = project.layout.buildDirectory.dir("outputs").get().asFile
            if (!outputsDir.exists()) {
                emptyList()
            } else {
                outputsDir.walkTopDown()
                    .filter { it.isFile && (it.extension == "apk" || it.extension == "aab") }
                    .toList()
            }
        }
        val offenders = mutableListOf<String>()
        artifacts.forEach { artifact ->
            val packagedText = runCatching {
                java.util.zip.ZipFile(artifact).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .joinToString("\n") { entry ->
                            runCatching {
                                zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                            }.getOrDefault("")
                        }
                }
            }.getOrNull()
            if (packagedText != null) {
                secretScanPatterns.forEach { (label, pattern) ->
                    if (pattern.containsMatchIn(packagedText)) {
                        offenders += "${artifact.relativeTo(rootProject.projectDir)} ($label)"
                    }
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Potential OpenAlex secret embedded in a packaged artifact:\n" +
                    offenders.joinToString("\n") { "  - $it" },
            )
        }
        if (artifacts.isEmpty()) {
            logger.lifecycle(
                "checkNoOpenAlexSecretInArtifacts: no APK/AAB found under build/outputs; " +
                    "run :app:assembleDebug (or :app:assembleRelease) first for a packaged-artifact check.",
            )
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(rootProject.tasks.named("checkNoOpenAlexSecret"))
        }
        // Matches the flavored assemble tasks too (assembleDemoDebug, assembleIntegrationRelease, ...).
        tasks.matching { Regex("^assemble[A-Za-z]*(Debug|Release)$").matches(it.name) }.configureEach {
            finalizedBy(rootProject.tasks.named("checkNoOpenAlexSecretInArtifacts"))
        }
    }
}
