import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Optional release signing, populated from a git-ignored keystore.properties at the project root.
// When absent (fresh clone / CI without secrets) the release build falls back to debug signing so it
// still produces an installable artifact. See keystore.properties.template for the expected keys.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.checkin.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexusai.checkin.app"
        minSdk = 34
        targetSdk = 36
        // Sourced from gradle.properties (VERSION_CODE / VERSION_NAME) — the single source of
        // truth. Override per-build with -PVERSION_CODE / -PVERSION_NAME. Fallbacks keep a fresh
        // checkout building if the properties are ever absent.
        versionCode = (project.findProperty("VERSION_CODE") as String? ?: "1").toInt()
        versionName = project.findProperty("VERSION_NAME") as String? ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Request native debug symbols in the bundle. Today's only native libs (ML Kit, CameraX)
            // ship pre-stripped by their vendors, so nothing is extracted and Play's "missing native
            // symbols" warning persists — this is future-proofing for any first-party NDK code and
            // costs nothing (symbols are stored server-side and stripped before delivery).
            ndk {
                debugSymbolLevel = "FULL"
            }
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Needed for BuildConfig.DEBUG, which gates the debug-only nudge harness in Settings.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // CameraX
    implementation("androidx.camera:camera-core:1.5.0")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")

    // Biometric fallback (device unlock after repeated face-detection failures)
    implementation("androidx.biometric:biometric:1.1.0")

    // Periodic evaluation pass for engagement nudges (see notify/engagement/NudgeWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ML Kit Face Detection (bundled, works offline)
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The open-source licence list in ui/about/OpenSourceLibraries.kt is hand-written: it groups ~220
// resolved artifacts into the upstream projects a reader can act on, and carries the three
// non-Apache corrections (ML Kit terms, the Android SDK licence, CameraX's embedded libyuv BSD) that
// a generator reading POMs alone gets wrong when a POM is silent. What it cannot do is notice a new
// dependency, so this task supplies the half that can be automated: every group id on the release
// runtime classpath must be covered by some entry's `coordinates`, or the build fails naming it.
val licenseSourceFile = layout.projectDirectory
    .file("src/main/java/com/checkin/app/ui/about/OpenSourceLibraries.kt")

tasks.register("verifyLicenseCoverage") {
    group = "verification"
    description = "Fails if a group id on the release runtime classpath has no licence entry."

    val source = licenseSourceFile
    val classpath = configurations.named("releaseRuntimeClasspath")
    inputs.file(source)

    doLast {
        // Matches the `coordinates = "..."` line of each entry; the group is everything before the
        // first colon, so "androidx.camera:*" and "org.jspecify:jspecify" both yield their group.
        val declared = Regex("""coordinates\s*=\s*"([^"]+)"""")
            .findAll(source.asFile.readText())
            .map { it.groupValues[1].substringBefore(':') }
            .toList()
        check(declared.isNotEmpty()) {
            "Parsed no coordinates out of ${source.asFile.name}. The entry format changed, and this " +
                "check would otherwise pass by finding nothing to compare against."
        }

        val resolved = classpath.get().incoming.resolutionResult.allComponents
            .mapNotNull { (it.id as? ModuleComponentIdentifier)?.group }
            .toSortedSet()

        val uncovered = resolved.filterNot { group ->
            declared.any { pattern ->
                // "androidx.*" covers androidx and everything beneath it; anything else is exact.
                val prefix = pattern.removeSuffix(".*")
                if (prefix == pattern) group == pattern else group == prefix || group.startsWith("$prefix.")
            }
        }

        check(uncovered.isEmpty()) {
            "These group ids ship in the APK with no entry in ${source.asFile.name}:\n" +
                uncovered.joinToString("\n") { "  - $it" } +
                "\n\nAdd an entry per upstream project, taking the licence from that artifact's POM " +
                "rather than assuming Apache-2.0."
        }

        logger.lifecycle("Licence coverage: ${resolved.size} group ids, all covered by ${declared.size} entries.")
    }
}

tasks.named("check") { dependsOn("verifyLicenseCoverage") }
