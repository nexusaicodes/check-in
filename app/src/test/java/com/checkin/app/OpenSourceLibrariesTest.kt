package com.checkin.app

import com.checkin.app.ui.about.LibraryLicense
import com.checkin.app.ui.about.OPEN_SOURCE_LIBRARIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape of a hand-maintained list. These tests cannot see the Gradle graph, so they
 * cannot prove the list is complete — they only stop it degrading into blanks and duplicates.
 */
class OpenSourceLibrariesTest {

    @Test
    fun `every entry is fully populated`() {
        assertTrue(OPEN_SOURCE_LIBRARIES.isNotEmpty())
        OPEN_SOURCE_LIBRARIES.forEach { library ->
            assertTrue(library.name, library.name.isNotBlank())
            assertTrue(library.name, library.coordinates.isNotBlank())
            assertTrue(library.name, library.copyright.isNotBlank())
            assertTrue(library.name, library.licenses.isNotEmpty())
            // An empty note would render as a stray gap rather than as nothing.
            assertFalse(library.name, library.note?.isBlank() == true)
        }
    }

    @Test
    fun `coordinates are unique so no project is listed twice`() {
        val coordinates = OPEN_SOURCE_LIBRARIES.map { it.coordinates }
        assertEquals(coordinates.size, coordinates.toSet().size)
    }

    @Test
    fun `ml kit is not passed off as apache licensed`() {
        val mlKit = OPEN_SOURCE_LIBRARIES.single { it.coordinates.startsWith("com.google.mlkit") }
        assertEquals(listOf(LibraryLicense.ML_KIT_TERMS), mlKit.licenses)
    }

    @Test
    fun `google's own-terms artifacts are never listed as apache`() {
        // Play services and ODML declare the Android SDK license in their POMs, not Apache-2.0.
        listOf("com.google.android.gms", "com.google.android.odml").forEach { group ->
            val entry = OPEN_SOURCE_LIBRARIES.single { it.coordinates.startsWith(group) }
            assertFalse(entry.name, entry.licenses.contains(LibraryLicense.APACHE_2_0))
            assertTrue(entry.name, entry.licenses.contains(LibraryLicense.ANDROID_SDK_TERMS))
        }
    }

    @Test
    fun `the transitive ml kit stack is attributed, not just the direct dependencies`() {
        // None of these is declared in app/build.gradle.kts; every one of them is redistributed in
        // the APK, so a list built from the dependency block alone would omit all four.
        listOf(
            "com.google.android.datatransport",
            "com.google.firebase",
            "com.google.android.gms",
            "com.google.android.odml",
        ).forEach { group ->
            assertTrue(group, OPEN_SOURCE_LIBRARIES.any { it.coordinates.startsWith(group) })
        }
    }

    @Test
    fun `camerax declares the bsd component alongside apache`() {
        val cameraX = OPEN_SOURCE_LIBRARIES.single { it.coordinates.startsWith("androidx.camera") }
        assertTrue(cameraX.licenses.containsAll(listOf(LibraryLicense.APACHE_2_0, LibraryLicense.BSD)))
    }

    @Test
    fun `apache is the one license whose text ships with the app`() {
        assertTrue(LibraryLicense.APACHE_2_0.bundled)
        LibraryLicense.entries.filter { it != LibraryLicense.APACHE_2_0 }
            .forEach { assertFalse(it.name, it.bundled) }
    }

    @Test
    fun `every license points somewhere a reader can verify it`() {
        LibraryLicense.entries.forEach {
            assertTrue(it.name, it.url.startsWith("https://"))
            assertTrue(it.name, it.displayName.isNotBlank())
        }
    }
}
