import com.android.build.api.artifact.SingleArtifact
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

fun String.toBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
}

/**
 * API level for the build-managed test device. ATD images are published for a narrower range than
 * ordinary system images, so this is deliberately below the project's compileSdk — it exercises the
 * app well above its minSdk of 26 while keeping the lightest available image.
 */
val ATD_API_LEVEL = 33

/**
 * The deployed PaperReader service. This is the `production` flavor's address and it is deliberately
 * a constant, not a property: `paperReaderBackendBaseUrl` is conventionally set machine-globally to
 * a loopback address for local development, and a "production" build that silently inherited that
 * would be worse than no production build at all. A different address is a different flavor.
 *
 * The Retrofit paths in `PaperReaderApi` are relative (`v1/papers/...`), so this is the bare origin.
 */
val PRODUCTION_BACKEND_BASE_URL = "https://barretolab.tech/"

/** The only external input any flavor takes, and only `integration` reads it. It is a non-secret
 * address, never a credential — an OpenAlex key must not reach the APK (see TECHNICAL_DEBT.md,
 * deliberate design choices). Absent or blank, `integration` targets the deployed backend rather
 * than failing; what it must never do is fall back to demo data. */
val paperReaderBackendBaseUrl =
    providers.gradleProperty("paperReaderBackendBaseUrl").orNull
        ?.takeIf { it.isNotBlank() }
        ?: PRODUCTION_BACKEND_BASE_URL

/** Mirrors `BackendConfiguration.resolve` so a misconfigured HTTP build fails at assembly time with
 * an actionable message instead of at first launch. */
fun backendBaseUrlProblem(rawUrl: String, requireHttps: Boolean): String? {
    val url = rawUrl.trim()
    if (url.isEmpty()) {
        return "no PaperReader backend base URL was supplied. Pass -PpaperReaderBackendBaseUrl=<url> " +
            "or set it in gradle.properties; an HTTP flavor never falls back to demo data."
    }
    val uri = runCatching { URI(url) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    if (uri == null || uri.host.isNullOrBlank() || (scheme != "http" && scheme != "https")) {
        return "\"$url\" is not a usable http(s) base URL."
    }
    if (requireHttps && scheme != "https") {
        return "\"$url\" is cleartext. This build must use https."
    }
    if (scheme == "http" && uri.host !in listOf("10.0.2.2", "localhost", "127.0.0.1")) {
        return "\"$url\" is cleartext for a host outside the debug loopback allowlist " +
            "(10.0.2.2, localhost, 127.0.0.1). Use https for any other host."
    }
    return null
}

/**
 * Release signing material, held by the operator in `~/.gradle/gradle.properties` and never in the
 * repository. All four must be present for the config to be wired at all, so `demo` and
 * `integration` release builds still assemble on a machine with no keystore; only
 * `assembleProductionRelease` insists on them (see `validateProductionReleaseSigning` below).
 */
val RELEASE_SIGNING_PROPERTIES = listOf(
    "paperReaderReleaseStoreFile",
    "paperReaderReleaseStorePassword",
    "paperReaderReleaseKeyAlias",
    "paperReaderReleaseKeyPassword",
)

val releaseSigningProperties: Map<String, String> = RELEASE_SIGNING_PROPERTIES
    .mapNotNull { name ->
        providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }?.let { name to it }
    }
    .toMap()

val hasReleaseSigningMaterial = releaseSigningProperties.size == RELEASE_SIGNING_PROPERTIES.size

/** The flavors that bind the HTTP remote source, paired with the address each one carries. */
val httpFlavorBaseUrls = mapOf(
    "integration" to paperReaderBackendBaseUrl,
    "production" to PRODUCTION_BACKEND_BASE_URL,
)

// Screenshot goldens are checked into version control (not build/outputs) so they review like any
// other source file; see app/src/test/screenshots/README.md for the record/verify workflow.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

android {
    namespace = "com.example.paperReader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.paperReader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Registered only when every property resolves, so a machine without the keystore can still
        // build every release variant except the one that has to be signed to be useful.
        if (hasReleaseSigningMaterial) {
            create("release") {
                storeFile = file(releaseSigningProperties.getValue("paperReaderReleaseStoreFile"))
                storePassword = releaseSigningProperties.getValue("paperReaderReleaseStorePassword")
                keyAlias = releaseSigningProperties.getValue("paperReaderReleaseKeyAlias")
                keyPassword = releaseSigningProperties.getValue("paperReaderReleaseKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningMaterial) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // The backend mode is a property of the build, never something inferred at runtime from
    // whether configuration happens to be complete (see design.md decision 1). `demo` needs no
    // backend at all; `integration` and `production` are both real HTTP builds and neither can ever
    // show fixture data — the fakes are not on their classpath.
    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("String", "PAPER_READER_REMOTE_MODE", "\"fake\"")
            buildConfigField("String", "PAPER_READER_BACKEND_BASE_URL", "\"\"")
        }
        // A backend you supply: defaults to the deployed service, redirected at a local server with
        // -PpaperReaderBackendBaseUrl. Its debug variant is the only build permitted cleartext.
        create("integration") {
            dimension = "environment"
            applicationIdSuffix = ".integration"
            versionNameSuffix = "-integration"
            buildConfigField("String", "PAPER_READER_REMOTE_MODE", "\"http\"")
            buildConfigField(
                "String",
                "PAPER_READER_BACKEND_BASE_URL",
                paperReaderBackendBaseUrl.toBuildConfigString(),
            )
        }
        // The deployed service, and nothing else. No suffix: this flavor owns the shippable
        // identity. No property is read, so no configuration a developer forgot about can move it.
        create("production") {
            dimension = "environment"
            buildConfigField("String", "PAPER_READER_REMOTE_MODE", "\"http\"")
            buildConfigField(
                "String",
                "PAPER_READER_BACKEND_BASE_URL",
                PRODUCTION_BACKEND_BASE_URL.toBuildConfigString(),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }

        // System animations make Compose assertions race against transitions rather than state.
        animationsDisabled = true

        /**
         * Instrumentation runs against a build-managed device so they are reproducible: Gradle
         * downloads the image, boots it, runs the tests, and tears it down, with the device
         * declared here rather than in whatever AVD a given machine happens to have.
         *
         * `aosp-atd` is an Automated Test Device image — no Play Store, no GMS, no SystemUI — which
         * removes the background services that are a well-known source of instrumentation
         * flakiness, and is markedly lighter than a `google_apis_playstore` image. On an ARM host
         * (Apple Silicon) AGP selects the arm64-v8a variant automatically, so it runs natively.
         *
         * Any AVD you have created by hand is left alone; this is an addition, not a replacement,
         * and remains the right tool for a manual walkthrough.
         */
        managedDevices {
            localDevices {
                create("atd") {
                    device = "Pixel 6"
                    apiLevel = ATD_API_LEVEL
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    sourceSets {
        getByName("test") {
            // Exposes the Room schema exports (below) to MigrationTestHelper in JVM unit tests.
            assets.srcDirs("$projectDir/schemas")
        }
        getByName("androidTest") {
            // One copy of the pinned v1 contract bundle, shared with the JVM consumer tests.
            resources.srcDirs("$projectDir/src/test/resources")
        }

        // `integration` and `production` run the same code against different addresses, so their
        // Hilt bindings, Internet permission, and the tests asserting both are compiled from one
        // shared set rather than duplicated per flavor. Note `srcDir` (append) — `srcDirs` would
        // replace the conventional `src/<flavor>/java` and silently drop flavor-specific sources.
        httpFlavorBaseUrls.keys.forEach { flavor ->
            val capitalized = flavor.replaceFirstChar(Char::uppercase)
            getByName(flavor) {
                java.srcDir("$projectDir/src/http/java")
                kotlin.srcDir("$projectDir/src/http/java")
                manifest.srcFile("$projectDir/src/http/AndroidManifest.xml")
            }
            getByName("test$capitalized") {
                java.srcDir("$projectDir/src/testHttp/java")
                kotlin.srcDir("$projectDir/src/testHttp/java")
            }
            getByName("androidTest$capitalized") {
                java.srcDir("$projectDir/src/androidTestHttp/java")
                kotlin.srcDir("$projectDir/src/androidTestHttp/java")
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

/**
 * Variants that must carry no cleartext exception at all. `integrationDebug` is deliberately absent:
 * it is the one build permitted to reach a loopback development server in the clear. Production has
 * no loopback story, so both of its build types are held to the release standard.
 */
val cleartextFreeVariants = listOf("integrationRelease", "productionDebug", "productionRelease")

// Assembling an HTTP APK requires a usable backend address. This is wired to `assemble` rather than
// `preBuild` on purpose: unit tests must stay runnable without a backend so they can assert the
// misconfiguration outcomes themselves.
androidComponents {
    httpFlavorBaseUrls.forEach { (flavor, configuredUrl) ->
        onVariants(selector().withFlavor("environment" to flavor)) { variant ->
            val variantName = variant.name
            val capitalized = variantName.replaceFirstChar(Char::uppercase)
            // Production requires https in debug too — it has no loopback allowance to fall back on.
            val requireHttps = flavor == "production" || variant.buildType == "release"
            val validate = tasks.register("validate${capitalized}BackendUrl") {
                group = "verification"
                description = "Fails when the $variantName backend base URL is missing or unusable."
                doLast {
                    val problem = backendBaseUrlProblem(configuredUrl, requireHttps)
                    if (problem != null) {
                        throw GradleException("Cannot assemble $variantName: $problem")
                    }
                }
            }
            tasks.matching { it.name == "assemble$capitalized" }.configureEach { dependsOn(validate) }
        }
    }

    // AND-003 exit criterion: the debug loopback allowance must not be able to reach a build that is
    // not entitled to it. Release unit tests are not part of `testBuildType`, so this is asserted
    // against the real merged manifest and packaged resources instead.
    cleartextFreeVariants.forEach { variantName ->
        onVariants(selector().withName(variantName)) { variant ->
            val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            val packagedResources = layout.buildDirectory.dir("intermediates/merged_res/$variantName")
            val capitalized = variantName.replaceFirstChar(Char::uppercase)
            tasks.register("verify${capitalized}NetworkPolicy") {
                group = "verification"
                description = "Fails if the $variantName manifest or resources carry a cleartext exception."
                inputs.file(mergedManifest)
                doLast {
                    val manifest = mergedManifest.get().asFile.readText()
                    val offenders = buildList {
                        if (manifest.contains("networkSecurityConfig")) {
                            add("the merged manifest sets android:networkSecurityConfig")
                        }
                        if (manifest.contains("usesCleartextTraffic")) {
                            add("the merged manifest sets android:usesCleartextTraffic")
                        }
                        if (!manifest.contains("android.permission.INTERNET")) {
                            add("the merged manifest is missing android.permission.INTERNET")
                        }
                        val resourcesDir = packagedResources.get().asFile
                        if (resourcesDir.isDirectory &&
                            resourcesDir.walkTopDown().any { it.name == "network_security_config.xml" }
                        ) {
                            add("network_security_config.xml is packaged into the resources")
                        }
                    }
                    if (offenders.isNotEmpty()) {
                        throw GradleException("$variantName network policy violated: " + offenders.joinToString("; "))
                    }
                    logger.lifecycle("$variantName: Internet permission present, no cleartext exception.")
                }
            }
        }
    }

    // An unsigned production release is not a useful artifact, so it fails at assembly the same way
    // a missing backend address does. Other release variants stay buildable without a keystore.
    onVariants(selector().withName("productionRelease")) {
        val validateSigning = tasks.register("validateProductionReleaseSigning") {
            group = "verification"
            description = "Fails when release signing material is not configured."
            doLast {
                if (!hasReleaseSigningMaterial) {
                    val missing = RELEASE_SIGNING_PROPERTIES.filterNot { releaseSigningProperties.containsKey(it) }
                    throw GradleException(
                        "Cannot assemble productionRelease: release signing is not configured. " +
                            "Set ${missing.joinToString(", ")} in ~/.gradle/gradle.properties " +
                            "(see README, \"Release transport\"); they must never be committed."
                    )
                }
            }
        }
        tasks.matching { it.name == "assembleProductionRelease" }.configureEach {
            dependsOn(validateSigning)
        }
    }
}

/**
 * The consumer-contract gate (SHR-001). Runs the pinned-bundle integrity check and every suite
 * that asserts request semantics, response shapes, transport outcomes, access invariants, and
 * fake/HTTP parity. Android CI must run this on any change to build, remote, persistence, or
 * download code — and passing it does NOT close SHR-001/SHR-002, which stay open until provider CI
 * verifies the same canonical bundle and version (see docs/api-compatibility.md).
 */
tasks.register("verifyApiContract") {
    group = "verification"
    description = "Runs the JVM suite containing the pinned v1 consumer-contract and parity tests."
    // The contract suites live in the shared `test` source set and are flavor-agnostic, so running
    // them under one flavor covers all three; this is part of the run rather than an opt-in extra.
    dependsOn("testDemoDebugUnitTest")
}

/**
 * Instrumentation for both flavors on the build-managed device declared in `testOptions`.
 *
 * Separate from `verifyBothFlavors` on purpose: this boots an emulator, so it is much slower than
 * the JVM suites and needs a machine with hardware virtualization available.
 */
tasks.register("verifyConnected") {
    group = "verification"
    description = "Runs demo, integration and production instrumentation on the managed ATD device."
    dependsOn(
        "atdDemoDebugAndroidTest",
        "atdIntegrationDebugAndroidTest",
        "atdProductionDebugAndroidTest",
    )
}

/** One command per debt-register verification row; see docs/android-debt-verification-matrix.md. */
tasks.register("verifyDemo") {
    group = "verification"
    description = "Assembles and tests the demo (fake-data, no-network) variant."
    dependsOn("assembleDemoDebug", "testDemoDebugUnitTest", "lintDemoDebug")
}

tasks.register("verifyIntegration") {
    group = "verification"
    description = "Assembles and tests the integration variant. " +
        "Targets the deployed backend unless -PpaperReaderBackendBaseUrl says otherwise."
    dependsOn("assembleIntegrationDebug", "testIntegrationDebugUnitTest", "lintIntegrationDebug")
}

tasks.register("verifyProduction") {
    group = "verification"
    description = "Assembles and tests the production (deployed-backend) variant. Takes no input."
    dependsOn(
        "assembleProductionDebug",
        "testProductionDebugUnitTest",
        "lintProductionDebug",
        // Production's debug build is held to the same no-cleartext standard as its release build,
        // so the merged-manifest check belongs in the routine gate rather than a release-only one.
        "verifyProductionDebugNetworkPolicy",
    )
}

tasks.register("verifyAllFlavors") {
    group = "verification"
    description = "Assembles and tests all three flavors and runs the source/artifact OpenAlex-secret scans."
    dependsOn(
        "verifyDemo",
        "verifyIntegration",
        "verifyProduction",
        "verifyApiContract",
        // The repository-wide source and packaged-artifact scans that already enforce the
        // "no OpenAlex credential in the client" rule; see the root build script.
        rootProject.tasks.named("checkNoOpenAlexSecret"),
        rootProject.tasks.named("checkNoOpenAlexSecretInArtifacts"),
    )
}

/** Kept so existing invocations and documented snippets keep working now that there are three. */
tasks.register("verifyBothFlavors") {
    group = "verification"
    description = "Alias for verifyAllFlavors, which now covers three flavors rather than two."
    dependsOn("verifyAllFlavors")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlin.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.material)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
