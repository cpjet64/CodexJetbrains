package dev.curt.codexjb.codelens

/**
 * Represents a TODO comment found in source code.
 *
 * @property body The text content of the TODO (excluding the "TODO:" prefix)
 * @property offset The character offset in the source text where the TODO body starts
 */
data class TodoItem(
    val body: String,
    val offset: Int
)

/**
 * Parser for TODO comments in source code.
 * Supports multiple comment styles: //, #, --, /* */
 * Ignores TODOs inside string literals.
 */
object TodoParser {
    private const val SPACE = ' '.code
    private const val TAB = '\t'.code
    private const val NEWLINE_LF = '\n'.code
    private const val NEWLINE_CR = '\r'.code

    /**
     * Parse all TODO comments from the given text.
     *
     * @param text The source code text to parse
     * @return List of TodoItem objects, one for each TODO found
     */
    fun parseTodos(text: String): List<TodoItem> {
        val todos = mutableListOf<TodoItem>()
        var pos = 0
        var inString = false
        var stringDelimiter: Char? = null

        while (pos < text.length) {
            val char = text[pos]

            // Track string literals to skip TODOs inside strings
            if (!inString) {
                when (char) {
                    '"', '\'', '`' -> {
                        inString = true
                        stringDelimiter = char
                        pos++
                        continue
                    }
                }
            } else {
                // Inside string - check for escape sequences and closing delimiter
                if (char == '\\' && pos + 1 < text.length) {
                    // Skip escaped character
                    pos += 2
                    continue
                } else if (char == stringDelimiter) {
                    inString = false
                    stringDelimiter = null
                    pos++
                    continue
                }
                pos++
                continue
            }

            // Not in string - check for comments
            when {
                // Single-line comment: //
                pos + 1 < text.length && char == '/' && text[pos + 1] == '/' -> {
                    // Skip repeated slashes (e.g., ///)
                    var commentStart = pos + 2
                    while (commentStart < text.length && text[commentStart] == '/') {
                        commentStart++
                    }

                    // Find end of line
                    var lineEnd = commentStart
                    while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') {
                        lineEnd++
                    }

                    // Check for TODO in this comment
                    val todoOffset = matchKeyword(text, commentStart, "TODO", ignoreCase = true)
                    if (todoOffset != -1) {
                        val todoBody = extractTodoBody(text, todoOffset, lineEnd)
                        todos.add(TodoItem(todoBody, todoOffset))
                    }

                    pos = lineEnd
                }

                // Hash comment: #
                char == '#' -> {
                    val commentStart = pos + 1

                    // Find end of line
                    var lineEnd = commentStart
                    while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') {
                        lineEnd++
                    }

                    // Check for TODO in this comment
                    val todoOffset = matchKeyword(text, commentStart, "TODO", ignoreCase = true)
                    if (todoOffset != -1) {
                        val todoBody = extractTodoBody(text, todoOffset, lineEnd)
                        todos.add(TodoItem(todoBody, todoOffset))
                    }

                    pos = lineEnd
                }

                // SQL/Lua comment: --
                pos + 1 < text.length && char == '-' && text[pos + 1] == '-' -> {
                    val commentStart = pos + 2

                    // Find end of line
                    var lineEnd = commentStart
                    while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') {
                        lineEnd++
                    }

                    // Check for TODO in this comment
                    val todoOffset = matchKeyword(text, commentStart, "TODO", ignoreCase = true)
                    if (todoOffset != -1) {
                        val todoBody = extractTodoBody(text, todoOffset, lineEnd)
                        todos.add(TodoItem(todoBody, todoOffset))
                    }

                    pos = lineEnd
                }

                // Multi-line comment: /* */
                pos + 1 < text.length && char == '/' && text[pos + 1] == '*' -> {
                    val commentStart = pos + 2

                    // Find closing */
                    var commentEnd = commentStart
                    while (commentEnd + 1 < text.length) {
                        if (text[commentEnd] == '*' && text[commentEnd + 1] == '/') {
                            break
                        }
                        commentEnd++
                    }

                    if (commentEnd + 1 < text.length) {
                        // Search for TODO with multi-line whitespace skipping
                        val todoOffset = matchKeywordMultiline(text, commentStart, "TODO", ignoreCase = true)
                        if (todoOffset != -1) {
                            // Find end of TODO text (either end of comment or newline)
                            var todoEnd = todoOffset
                            while (todoEnd < commentEnd && text[todoEnd] != '\n' && text[todoEnd] != '\r') {
                                todoEnd++
                            }
                            val todoBody = extractTodoBody(text, todoOffset, todoEnd)
                            todos.add(TodoItem(todoBody, todoOffset))
                        }

                        pos = commentEnd + 2
                    } else {
                        pos = commentEnd
                    }
                }

                else -> pos++
            }
        }

        return todos
    }

    /**
     * Extract the TODO body text from start offset to end offset.
     */
    private fun extractTodoBody(text: String, start: Int, end: Int): String {
        return text.substring(start, end).trim()
    }

    /**
     * Skip whitespace characters (space and tab only, for single-line comments).
     *
     * @param text The text to scan
     * @param pos Starting position
     * @return Position after whitespace, or original pos if no whitespace
     */
    private fun skipWhitespace(text: String, pos: Int): Int {
        var p = pos
        while (p < text.length) {
            val code = text[p].code
            if (code != SPACE && code != TAB) {
                break
            }
            p++
        }
        return p
    }

    /**
     * Skip whitespace and line decorations in multi-line comments (space, tab, newline, asterisks).
     *
     * @param text The text to scan
     * @param pos Starting position
     * @return Position after whitespace and decorations
     */
    private fun skipMultilineWhitespace(text: String, pos: Int): Int {
        var p = pos
        while (p < text.length) {
            val char = text[p]
            val code = char.code
            // Skip spaces, tabs, newlines, carriage returns, and leading asterisks (for Javadoc)
            if (code != SPACE && code != TAB && code != NEWLINE_LF && code != NEWLINE_CR && char != '*') {
                break
            }
            p++
        }
        return p
    }

    /**
     * Check if a character is a word character (0-9, A-Z, a-z, _).
     */
    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }

    /**
     * Match a keyword at the given position with word boundary checking.
     *
     * @param text The text to search in
     * @param pos Starting position
     * @param keyword The keyword to match (e.g., "TODO")
     * @param ignoreCase Whether to ignore case when matching
     * @return Position of the text after the keyword and optional punctuation, or -1 if no match
     */
    private fun matchKeyword(text: String, pos: Int, keyword: String, ignoreCase: Boolean): Int {
        var p = skipWhitespace(text, pos)

        // Check if keyword matches
        if (p + keyword.length > text.length) {
            return -1
        }

        val textSubstring = text.substring(p, p + keyword.length)
        val matches = if (ignoreCase) {
            textSubstring.equals(keyword, ignoreCase = true)
        } else {
            textSubstring == keyword
        }

        if (!matches) {
            return -1
        }

        // Check word boundary (next character must not be a word character)
        val afterKeyword = p + keyword.length
        if (afterKeyword < text.length && isWordChar(text[afterKeyword])) {
            return -1  // Not a word boundary (e.g., "TODONT")
        }

        // Skip optional : or - after keyword
        var afterPunctuation = afterKeyword
        if (afterPunctuation < text.length && (text[afterPunctuation] == ':' || text[afterPunctuation] == '-')) {
            afterPunctuation++
        }

        // Skip whitespace after punctuation
        afterPunctuation = skipWhitespace(text, afterPunctuation)

        return afterPunctuation
    }

    /**
     * Match a keyword in multi-line comments with Javadoc-style decoration skipping.
     */
    private fun matchKeywordMultiline(text: String, pos: Int, keyword: String, ignoreCase: Boolean): Int {
        var p = skipMultilineWhitespace(text, pos)

        // Check if keyword matches
        if (p + keyword.length > text.length) {
            return -1
        }

        val textSubstring = text.substring(p, p + keyword.length)
        val matches = if (ignoreCase) {
            textSubstring.equals(keyword, ignoreCase = true)
        } else {
            textSubstring == keyword
        }

        if (!matches) {
            return -1
        }

        // Check word boundary (next character must not be a word character)
        val afterKeyword = p + keyword.length
        if (afterKeyword < text.length && isWordChar(text[afterKeyword])) {
            return -1  // Not a word boundary (e.g., "TODONT")
        }

        // Skip optional : or - after keyword
        var afterPunctuation = afterKeyword
        if (afterPunctuation < text.length && (text[afterPunctuation] == ':' || text[afterPunctuation] == '-')) {
            afterPunctuation++
        }

        // Skip whitespace after punctuation (single-line only here)
        afterPunctuation = skipWhitespace(text, afterPunctuation)

        return afterPunctuation
    }
}
