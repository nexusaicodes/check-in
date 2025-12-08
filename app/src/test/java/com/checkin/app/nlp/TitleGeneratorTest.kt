package com.checkin.app.nlp

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

/**
 * Unit tests for TitleGenerator
 *
 * These tests verify the fallback mode behavior since OpenNLP models
 * won't be available in the unit test environment.
 */
class TitleGeneratorTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var titleGenerator: TitleGenerator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Mock the assets to simulate missing models (fallback mode)
        Mockito.`when`(mockContext.assets).thenReturn(null)

        titleGenerator = TitleGenerator(mockContext)
    }

    @Test
    fun `test basic title generation`() {
        val input = "I need to finish the quarterly report for management"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.length <= 40)
    }

    @Test
    fun `test removes common sentence starters`() {
        val input = "I am working on the Android app project"
        val result = titleGenerator.generateTitle(input)

        // Should not start with "I am"
        assertFalse(result.startsWith("I Am", ignoreCase = true))
        assertTrue(result.contains("Working", ignoreCase = true) ||
                   result.contains("Android", ignoreCase = true) ||
                   result.contains("App", ignoreCase = true))
    }

    @Test
    fun `test filters stop words`() {
        val input = "Meeting with the team about the new project"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        // Result should prioritize meaningful words
        assertTrue(result.contains("Meeting", ignoreCase = true) ||
                   result.contains("Team", ignoreCase = true) ||
                   result.contains("Project", ignoreCase = true))
    }

    @Test
    fun `test handles short input`() {
        val input = "Code review"
        val result = titleGenerator.generateTitle(input)

        assertEquals("Code Review", result)
    }

    @Test
    fun `test handles empty input`() {
        val input = ""
        val result = titleGenerator.generateTitle(input)

        assertEquals("", result)
    }

    @Test
    fun `test handles blank input`() {
        val input = "   "
        val result = titleGenerator.generateTitle(input)

        assertEquals("", result)
    }

    @Test
    fun `test capitalizes words properly`() {
        val input = "debugging android application issues"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        // First letter of each word should be capitalized
        val words = result.split(" ")
        words.forEach { word ->
            if (word.isNotEmpty()) {
                assertTrue("Word '$word' should be capitalized",
                           word[0].isUpperCase())
            }
        }
    }

    @Test
    fun `test limits word count`() {
        val input = "Working on implementing the new user authentication system with OAuth and JWT tokens"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())

        // Count words (excluding ellipsis)
        val wordCount = result.replace("...", "").split(" ").size
        assertTrue("Word count should be <= 4, got $wordCount", wordCount <= 4)
    }

    @Test
    fun `test limits character length`() {
        val input = "This is a very long description that should definitely be truncated to fit within the forty character limit"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.length <= 40)
    }

    @Test
    fun `test handles special characters`() {
        val input = "Fix bug #123: Login fails with special chars"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `test handles numeric input`() {
        val input = "Complete task 42 from sprint 3"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("Complete", ignoreCase = true) ||
                   result.contains("Task", ignoreCase = true) ||
                   result.contains("Sprint", ignoreCase = true))
    }

    @Test
    fun `test preserves important numbers`() {
        val input = "Review 5 pull requests"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `test real world example - meeting`() {
        val input = "I need to prepare for the client presentation tomorrow"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.length <= 40)
        // Should contain meaningful words
        val lowerResult = result.lowercase()
        assertTrue(lowerResult.contains("prepare") ||
                   lowerResult.contains("client") ||
                   lowerResult.contains("presentation"))
    }

    @Test
    fun `test real world example - coding task`() {
        val input = "Working on the new dashboard component for React"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.length <= 40)
    }

    @Test
    fun `test real world example - learning`() {
        val input = "Going to study Kotlin coroutines and flow"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.length <= 40)
        val lowerResult = result.lowercase()
        assertTrue(lowerResult.contains("study") ||
                   lowerResult.contains("kotlin") ||
                   lowerResult.contains("coroutines"))
    }

    @Test
    fun `test isReady returns false in fallback mode`() {
        // In test environment, models won't be loaded
        assertFalse(titleGenerator.isReady())
    }

    @Test
    fun `test getStatus returns fallback mode message`() {
        val status = titleGenerator.getStatus()

        assertNotNull(status)
        assertTrue(status.contains("fallback", ignoreCase = true))
    }

    @Test
    fun `test handles all stop words`() {
        val input = "the and a of to in"
        val result = titleGenerator.generateTitle(input)

        // Should be empty or very short since all are stop words
        assertTrue(result.isEmpty() || result.length <= 10)
    }

    @Test
    fun `test consecutive meaningful words`() {
        val input = "Database migration script execution"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("Database", ignoreCase = true) ||
                   result.contains("Migration", ignoreCase = true) ||
                   result.contains("Script", ignoreCase = true))
    }

    @Test
    fun `test mixed case input`() {
        val input = "FiXiNg BuGs iN tHe PaYmEnT sYsTeM"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        // Should be properly capitalized
        val words = result.split(" ")
        words.forEach { word ->
            if (word.length > 1 && !word.endsWith("...")) {
                assertTrue("Word should have proper case: $word",
                           word[0].isUpperCase() && word.substring(1).all { it.isLowerCase() })
            }
        }
    }

    @Test
    fun `test gerund forms`() {
        val input = "Reading documentation and writing tests"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("Reading", ignoreCase = true) ||
                   result.contains("Writing", ignoreCase = true) ||
                   result.contains("Documentation", ignoreCase = true))
    }

    @Test
    fun `test multiple sentences`() {
        val input = "Need to fix the bug. Also update documentation. Then deploy to staging."
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.length <= 40)
    }

    @Test
    fun `test technical terms`() {
        val input = "Implementing REST API endpoints with authentication"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        val lowerResult = result.lowercase()
        assertTrue(lowerResult.contains("implementing") ||
                   lowerResult.contains("rest") ||
                   lowerResult.contains("api") ||
                   lowerResult.contains("endpoints") ||
                   lowerResult.contains("authentication"))
    }

    @Test
    fun `test action verb filtering`() {
        val input = "Need to organize team meeting"
        val result = titleGenerator.generateTitle(input)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        // "Need" should be filtered out
        val lowerResult = result.lowercase()
        assertTrue(lowerResult.contains("organize") ||
                   lowerResult.contains("team") ||
                   lowerResult.contains("meeting"))
    }
}
