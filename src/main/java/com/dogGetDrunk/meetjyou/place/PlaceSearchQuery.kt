package com.dogGetDrunk.meetjyou.place

/**
 * A search box input shaped for the place queries. Pure: no I/O.
 *
 * [terms] requires each 2-char ngram token as its own boolean-mode term rather than one phrase: a phrase
 * makes InnoDB read every candidate row to check token positions, while required terms are answered
 * from the full-text index alone. [likePattern] then checks adjacency on the few rows actually read.
 *
 * Terms use only tokens the ngram parser surely indexed in every name containing the query, so the
 * full-text candidates always include every LIKE match and both search paths return the same places:
 * - Characters other than letters, digits and '_' split words and never sit inside a token ("50%할인" is
 *   indexed as "50" and "할인", not "0%" or "%할")
 * - A one-character word is not indexed as a token (a prefix term would miss "느낌!표"), so it adds no term
 * - A token containing a stopword is never indexed, so it is left out
 */
class PlaceSearchQuery private constructor(
    /** Whitespace-free query, compared as-is against `search_name` for the exact-match lookup. */
    val exact: String,
    /** Boolean-mode terms, or null when no token is indexable: then no place can match. */
    val terms: String?,
    /** `%query%` with [LIKE_ESCAPE] escaping, for `LIKE ... ESCAPE '!'`. */
    val likePattern: String,
) {
    companion object {
        const val LIKE_ESCAPE = "!"
        private const val TOKEN_SIZE = 2
        private const val QUOTE = "\""
        private const val UNDERSCORE = '_'.code
        private val LIKE_SPECIALS = listOf(LIKE_ESCAPE, "%", "_")

        // MySQL's default InnoDB stopword list. The ngram parser leaves out every token that contains one
        // ("pi" contains "i"), so requiring such a token would match nothing; a phrase just skips it.
        private val STOPWORDS = listOf(
            "a", "about", "an", "are", "as", "at", "be", "by", "com", "de", "en", "for", "from", "how", "i",
            "in", "is", "it", "la", "of", "on", "or", "that", "the", "this", "to", "und", "was", "what",
            "when", "where", "who", "will", "with", "www",
        )

        fun of(compactQuery: String): PlaceSearchQuery {
            // A double quote would end a boolean-mode phrase early, so it never reaches the terms.
            val text = compactQuery.replace(QUOTE, "")
            return PlaceSearchQuery(compactQuery, buildTerms(text), buildLikePattern(text))
        }

        // Validation counts the quotes, so a query of one character without them can reach here: it has no
        // word of two characters and gets no terms either.
        private fun buildTerms(text: String): String? =
            splitWords(text).flatMap { termsOf(it) }.distinct().ifEmpty { null }?.joinToString(" ")

        private fun termsOf(word: IntArray): List<String> {
            return (0..word.size - TOKEN_SIZE)
                .map { String(word, it, TOKEN_SIZE) }
                .filterNot { isStopwordToken(it) }
                .map { "+\"$it\"" }
        }

        private fun splitWords(text: String): List<IntArray> {
            val words = mutableListOf<IntArray>()
            val current = mutableListOf<Int>()
            text.codePoints().forEach { codePoint ->
                if (isWordChar(codePoint)) {
                    current.add(codePoint)
                } else if (current.isNotEmpty()) {
                    words.add(current.toIntArray())
                    current.clear()
                }
            }
            if (current.isNotEmpty()) words.add(current.toIntArray())
            return words
        }

        // MySQL's full-text word characters: letters, digits and underscore.
        private fun isWordChar(codePoint: Int): Boolean =
            Character.isLetterOrDigit(codePoint) || codePoint == UNDERSCORE

        private fun isStopwordToken(token: String): Boolean {
            val lower = token.lowercase()
            return STOPWORDS.any { lower.contains(it) }
        }

        private fun buildLikePattern(text: String): String {
            var escaped = text
            LIKE_SPECIALS.forEach { escaped = escaped.replace(it, "$LIKE_ESCAPE$it") }
            return "%$escaped%"
        }
    }
}
