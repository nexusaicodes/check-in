package com.checkin.app.ui.about

/**
 * A license one of the bundled libraries is distributed under.
 *
 * [bundled] marks the ones whose full text ships in `res/raw`, so the Licenses screen can reproduce
 * them with no network — everything else links out. Apache-2.0 section 4 asks for the license text
 * itself, not merely its name, and this app has no way to fetch it at runtime.
 */
enum class LibraryLicense(val displayName: String, val url: String, val bundled: Boolean) {
    APACHE_2_0(
        displayName = "Apache License 2.0",
        url = "https://www.apache.org/licenses/LICENSE-2.0",
        bundled = true
    ),
    BSD(
        displayName = "BSD License",
        url = "https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/README.chromium",
        bundled = false
    ),

    /** Not an open-source license: ML Kit ships under Google's own terms. Named honestly here. */
    ML_KIT_TERMS(
        displayName = "ML Kit Terms of Service",
        url = "https://developers.google.com/ml-kit/terms",
        bundled = false
    )
}

/**
 * One entry on the Licenses screen. [coordinates] is a concrete Maven coordinate where the entry is
 * a single artifact and a `group.*` wildcard where it stands for a whole family.
 */
data class OpenSourceLibrary(
    val name: String,
    val coordinates: String,
    val copyright: String,
    val licenses: List<LibraryLicense>,
    val note: String? = null
)

/**
 * Everything redistributed inside the app, grouped by project rather than by artifact.
 *
 * The release runtime classpath resolves to ~220 artifacts, which is neither readable nor
 * maintainable as a list; they collapse into these seven upstream projects. Grouping is only sound
 * because the license is uniform within each group — where it isn't, the group is split out (see
 * CameraX, whose camera-core POM declares BSD alongside Apache-2.0).
 *
 * Keeping this by hand means it can drift when a dependency is added. `OpenSourceLibrariesTest`
 * guards the shape of the list, but it cannot see the Gradle graph — re-check this file when
 * `app/build.gradle.kts` gains a dependency from a new upstream project.
 */
val OPEN_SOURCE_LIBRARIES: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary(
        name = "AndroidX (Jetpack)",
        coordinates = "androidx.*",
        copyright = "Copyright © The Android Open Source Project",
        licenses = listOf(LibraryLicense.APACHE_2_0),
        note = "Core, Compose, Material 3, Lifecycle, Navigation, Room, WorkManager, Biometric " +
            "and their dependencies."
    ),
    OpenSourceLibrary(
        name = "CameraX",
        coordinates = "androidx.camera:*",
        copyright = "Copyright © The Android Open Source Project",
        licenses = listOf(LibraryLicense.APACHE_2_0, LibraryLicense.BSD),
        note = "camera-core embeds libyuv, which carries its own BSD license."
    ),
    OpenSourceLibrary(
        name = "Kotlin Standard Library",
        coordinates = "org.jetbrains.kotlin:*",
        copyright = "Copyright © JetBrains s.r.o. and Kotlin Programming Language contributors",
        licenses = listOf(LibraryLicense.APACHE_2_0)
    ),
    OpenSourceLibrary(
        name = "kotlinx.coroutines",
        coordinates = "org.jetbrains.kotlinx:*",
        copyright = "Copyright © JetBrains s.r.o.",
        licenses = listOf(LibraryLicense.APACHE_2_0)
    ),
    OpenSourceLibrary(
        name = "ML Kit Face Detection",
        coordinates = "com.google.mlkit:face-detection:16.1.7",
        copyright = "Copyright © Google LLC",
        licenses = listOf(LibraryLicense.ML_KIT_TERMS),
        note = "Runs entirely on the device. It is the face check that gates check-in and check-out."
    ),
    OpenSourceLibrary(
        name = "Guava ListenableFuture",
        coordinates = "com.google.guava:listenablefuture:1.0",
        copyright = "Copyright © The Guava Authors",
        licenses = listOf(LibraryLicense.APACHE_2_0)
    ),
    OpenSourceLibrary(
        name = "AutoValue Annotations",
        coordinates = "com.google.auto.value:auto-value-annotations:1.6.3",
        copyright = "Copyright © Google LLC",
        licenses = listOf(LibraryLicense.APACHE_2_0)
    )
)
