package com.checkin.app.nlp

import android.content.Context
import android.util.Log
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.tokenize.TokenizerME
import opennlp.tools.tokenize.TokenizerModel
import java.io.InputStream
import java.util.Locale

/**
 * TitleGenerator uses Apache OpenNLP to extract meaningful titles from text descriptions.
 *
 * Features:
 * - Tokenization and POS tagging
 * - Keyword extraction (nouns, proper nouns, gerunds)
 * - Stop word filtering
 * - Compound noun detection
 * - Multi-language support
 * - Graceful fallback when models unavailable
 */
class TitleGenerator(private val context: Context) {

    private var tokenizer: TokenizerME? = null
    private var posTagger: POSTaggerME? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "TitleGenerator"
        private const val MAX_TITLE_LENGTH = 40
        private const val MAX_WORDS = 4

        // English stop words to filter out
        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
            "to", "was", "will", "with", "am", "i", "me", "my", "myself",
            "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself",
            "yourselves", "they", "them", "their", "theirs", "themselves",
            "what", "which", "who", "whom", "this", "these", "those", "when",
            "where", "why", "how", "all", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "can", "just", "should", "now"
        )

        // Common action verbs to filter from start of sentence
        private val ACTION_VERBS = setOf(
            "need", "want", "going", "try", "trying", "work", "working",
            "make", "making", "do", "doing", "get", "getting", "have",
            "having", "take", "taking", "use", "using", "start", "starting",
            "continue", "continuing", "finish", "finishing"
        )

        // POS tags we want to keep for title generation
        private const val NOUN = "NN"           // Noun, singular
        private const val NOUN_PLURAL = "NNS"   // Noun, plural
        private const val PROPER_NOUN = "NNP"   // Proper noun, singular
        private const val PROPER_NOUN_PLURAL = "NNPS" // Proper noun, plural
        private const val GERUND = "VBG"        // Verb, gerund/present participle
        private const val ADJECTIVE = "JJ"      // Adjective
        private const val CARDINAL = "CD"       // Cardinal number
    }

    init {
        initializeModels()
    }

    /**
     * Initialize OpenNLP models from assets directory
     */
    private fun initializeModels() {
        try {
            // Load tokenizer model
            val tokenizerInputStream: InputStream? = try {
                context.assets.open("opennlp/en-token.bin")
            } catch (e: Exception) {
                Log.w(TAG, "Tokenizer model not found in assets, will use fallback")
                null
            }

            if (tokenizerInputStream != null) {
                val tokenizerModel = TokenizerModel(tokenizerInputStream)
                tokenizer = TokenizerME(tokenizerModel)
                tokenizerInputStream.close()
                Log.d(TAG, "Tokenizer model loaded successfully")
            }

            // Load POS tagger model
            val posInputStream: InputStream? = try {
                context.assets.open("opennlp/en-pos-maxent.bin")
            } catch (e: Exception) {
                Log.w(TAG, "POS tagger model not found in assets, will use fallback")
                null
            }

            if (posInputStream != null) {
                val posModel = POSModel(posInputStream)
                posTagger = POSTaggerME(posModel)
                posInputStream.close()
                Log.d(TAG, "POS tagger model loaded successfully")
            }

            isInitialized = (tokenizer != null && posTagger != null)

            if (isInitialized) {
                Log.i(TAG, "TitleGenerator initialized with OpenNLP models")
            } else {
                Log.w(TAG, "TitleGenerator initialized with fallback mode (models not available)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenNLP models: ${e.message}", e)
            isInitialized = false
        }
    }

    /**
     * Generate a concise title from the input description
     *
     * @param description The input text to process
     * @return A concise title (3-4 words, max 40 characters)
     */
    fun generateTitle(description: String): String {
        if (description.isBlank()) {
            return ""
        }

        return if (isInitialized) {
            generateTitleWithNLP(description)
        } else {
            generateTitleWithFallback(description)
        }
    }

    /**
     * Generate title using OpenNLP models (tokenization + POS tagging)
     */
    private fun generateTitleWithNLP(description: String): String {
        try {
            // Clean and prepare text
            val cleanedText = description.trim()

            // Tokenize the text
            val tokens = tokenizer?.tokenize(cleanedText) ?: return generateTitleWithFallback(description)

            // Get POS tags
            val tags = posTagger?.tag(tokens) ?: return generateTitleWithFallback(description)

            // Extract keywords based on POS tags
            val keywords = extractKeywords(tokens, tags)

            // Build title from keywords
            return buildTitle(keywords)

        } catch (e: Exception) {
            Log.e(TAG, "Error in NLP title generation: ${e.message}", e)
            return generateTitleWithFallback(description)
        }
    }

    /**
     * Extract relevant keywords based on POS tags
     */
    private fun extractKeywords(tokens: Array<String>, tags: Array<String>): List<String> {
        val keywords = mutableListOf<String>()
        var previousWasNoun = false
        var compoundNoun = StringBuilder()

        for (i in tokens.indices) {
            val token = tokens[i]
            val tag = tags[i]
            val tokenLower = token.lowercase(Locale.getDefault())

            // Skip stop words (unless it's a proper noun)
            if (tokenLower in STOP_WORDS && !tag.startsWith("NNP")) {
                if (previousWasNoun && compoundNoun.isNotEmpty()) {
                    keywords.add(compoundNoun.toString())
                    compoundNoun.clear()
                }
                previousWasNoun = false
                continue
            }

            // Skip action verbs at the start
            if (i == 0 && tokenLower in ACTION_VERBS) {
                continue
            }

            when {
                // Proper nouns - always keep and preserve case
                tag == PROPER_NOUN || tag == PROPER_NOUN_PLURAL -> {
                    if (previousWasNoun) {
                        compoundNoun.append(" ").append(token)
                    } else {
                        if (compoundNoun.isNotEmpty()) {
                            keywords.add(compoundNoun.toString())
                            compoundNoun.clear()
                        }
                        compoundNoun.append(token)
                    }
                    previousWasNoun = true
                }

                // Regular nouns
                tag == NOUN || tag == NOUN_PLURAL -> {
                    if (previousWasNoun) {
                        compoundNoun.append(" ").append(token.lowercase(Locale.getDefault()))
                    } else {
                        if (compoundNoun.isNotEmpty()) {
                            keywords.add(compoundNoun.toString())
                            compoundNoun.clear()
                        }
                        compoundNoun.append(token.lowercase(Locale.getDefault()))
                    }
                    previousWasNoun = true
                }

                // Gerunds (e.g., "running", "coding")
                tag == GERUND -> {
                    if (previousWasNoun && compoundNoun.isNotEmpty()) {
                        keywords.add(compoundNoun.toString())
                        compoundNoun.clear()
                    }
                    keywords.add(token.lowercase(Locale.getDefault()))
                    previousWasNoun = false
                }

                // Adjectives before nouns
                tag == ADJECTIVE -> {
                    // Only keep if followed by a noun
                    if (i + 1 < tags.size && isNounTag(tags[i + 1])) {
                        if (compoundNoun.isNotEmpty()) {
                            keywords.add(compoundNoun.toString())
                            compoundNoun.clear()
                        }
                        compoundNoun.append(token.lowercase(Locale.getDefault()))
                        previousWasNoun = false
                    }
                }

                // Cardinal numbers
                tag == CARDINAL -> {
                    if (i + 1 < tags.size && isNounTag(tags[i + 1])) {
                        if (compoundNoun.isNotEmpty()) {
                            keywords.add(compoundNoun.toString())
                            compoundNoun.clear()
                        }
                        compoundNoun.append(token)
                        previousWasNoun = false
                    }
                }

                else -> {
                    if (previousWasNoun && compoundNoun.isNotEmpty()) {
                        keywords.add(compoundNoun.toString())
                        compoundNoun.clear()
                    }
                    previousWasNoun = false
                }
            }
        }

        // Add any remaining compound noun
        if (compoundNoun.isNotEmpty()) {
            keywords.add(compoundNoun.toString())
        }

        return keywords
    }

    /**
     * Check if a POS tag is a noun tag
     */
    private fun isNounTag(tag: String): Boolean {
        return tag == NOUN || tag == NOUN_PLURAL ||
               tag == PROPER_NOUN || tag == PROPER_NOUN_PLURAL
    }

    /**
     * Build final title from extracted keywords
     */
    private fun buildTitle(keywords: List<String>): String {
        if (keywords.isEmpty()) {
            return ""
        }

        // Take up to MAX_WORDS keywords
        val selectedWords = keywords.take(MAX_WORDS)

        // Join words and capitalize
        var title = selectedWords.joinToString(" ")

        // Truncate if too long
        if (title.length > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH - 3) + "..."
        }

        // Capitalize first letter of each word (except for articles in the middle)
        return title.split(" ").joinToString(" ") { word ->
            if (word.length > 0) {
                word[0].uppercaseChar() + word.substring(1)
            } else {
                word
            }
        }
    }

    /**
     * Fallback title generation using simple regex and string manipulation
     * Used when OpenNLP models are not available
     */
    private fun generateTitleWithFallback(description: String): String {
        try {
            // Clean the text
            var text = description.trim()

            // Remove common sentence starters
            text = text.replace(Regex("^(i|I) (am|'m) ", RegexOption.IGNORE_CASE), "")
            text = text.replace(Regex("^(i|I) (need|want|will|have) to ", RegexOption.IGNORE_CASE), "")
            text = text.replace(Regex("^(going|trying) to ", RegexOption.IGNORE_CASE), "")

            // Split into words
            val words = text.split(Regex("\\s+"))
                .map { it.trim().replace(Regex("[^a-zA-Z0-9]"), "") }
                .filter { it.isNotEmpty() }

            // Filter out stop words
            val meaningfulWords = words.filter {
                it.lowercase(Locale.getDefault()) !in STOP_WORDS
            }

            // If we have no meaningful words, use first few words
            val selectedWords = if (meaningfulWords.isEmpty()) {
                words.take(MAX_WORDS)
            } else {
                meaningfulWords.take(MAX_WORDS)
            }

            // Join and capitalize
            var title = selectedWords.joinToString(" ")

            // Truncate if too long
            if (title.length > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH - 3) + "..."
            }

            // Capitalize first letter of each word
            return title.split(" ").joinToString(" ") { word ->
                if (word.length > 0) {
                    word[0].uppercaseChar() + word.substring(1).lowercase(Locale.getDefault())
                } else {
                    word
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback title generation: ${e.message}", e)
            // Last resort: take first 40 characters
            return description.take(MAX_TITLE_LENGTH).trim().let {
                if (it.length < description.length) "$it..." else it
            }
        }
    }

    /**
     * Check if OpenNLP models are loaded and ready
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get status information about the title generator
     */
    fun getStatus(): String {
        return when {
            isInitialized -> "OpenNLP models loaded (full NLP mode)"
            else -> "Running in fallback mode (models not loaded)"
        }
    }
}
