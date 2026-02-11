package io.typestream.compiler.ast

import io.typestream.compiler.types.schema.Schema

/**
 * Parses predicate expressions from strings into Predicate objects.
 * Supports expressions like:
 * - `.field == "value"` (string equality)
 * - `.field != "value"` (string inequality)
 * - `.field > 100` (numeric comparison)
 * - `.field >= 100`, `.field < 100`, `.field <= 100`
 * - `.field == 100` (numeric equality)
 * - `(.field > 10) && (.field < 100)` (logical AND)
 * - `(.field == "a") || (.field == "b")` (logical OR)
 * - `!(.field == "value")` (negation)
 * - `pattern` (regex match on entire record - fallback)
 */
object PredicateParser {

    /**
     * Parse a predicate expression string into a Predicate object.
     * Falls back to regex matching if the expression can't be parsed as a structured predicate.
     */
    fun parse(expr: String): Predicate {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) {
            return Predicate.alwaysTrue()  // Match everything
        }

        return try {
            parseOr(Tokenizer(trimmed))
        } catch (e: Exception) {
            // Fallback to regex matching for simple patterns
            Predicate.matches(trimmed)
        }
    }

    private fun parseOr(tokenizer: Tokenizer): Predicate {
        var left = parseAnd(tokenizer)

        while (tokenizer.match("||")) {
            val right = parseAnd(tokenizer)
            left = left.or(right)
        }

        return left
    }

    private fun parseAnd(tokenizer: Tokenizer): Predicate {
        var left = parseUnary(tokenizer)

        while (tokenizer.match("&&")) {
            val right = parseUnary(tokenizer)
            left = left.and(right)
        }

        return left
    }

    private fun parseUnary(tokenizer: Tokenizer): Predicate {
        if (tokenizer.match("!")) {
            return parseUnary(tokenizer).not()
        }
        return parsePrimary(tokenizer)
    }

    private fun parsePrimary(tokenizer: Tokenizer): Predicate {
        // Handle parenthesized expressions
        if (tokenizer.match("(")) {
            // Try structured expression first, fall back to regex pattern
            val saved = tokenizer.save()
            try {
                val expr = parseOr(tokenizer)
                tokenizer.consume(")")
                return expr
            } catch (e: Exception) {
                tokenizer.restore(saved)
                // Collect tokens until matching ")" as a regex pattern
                val parts = mutableListOf<String>()
                while (tokenizer.peek() != null && tokenizer.peek() != ")") {
                    parts.add(tokenizer.next()!!)
                }
                tokenizer.consume(")")
                return Predicate.matches(parts.joinToString(" "))
            }
        }

        // Handle field comparison: .field op value
        if (tokenizer.peek()?.startsWith(".") == true) {
            return parseComparison(tokenizer)
        }

        // Fallback: treat as regex pattern
        throw IllegalArgumentException("Expected field access or parenthesized expression")
    }

    private fun parseComparison(tokenizer: Tokenizer): Predicate {
        val field = tokenizer.next() ?: throw IllegalArgumentException("Expected field")

        // Extract field name (remove leading dot)
        val fieldName = field.trimStart('.')

        val operator = tokenizer.next() ?: throw IllegalArgumentException("Expected operator")
        val value = tokenizer.next() ?: throw IllegalArgumentException("Expected value")

        return when (operator) {
            "==" -> createEqualsPredicate(fieldName, value)
            "!=" -> createEqualsPredicate(fieldName, value).not()
            ">" -> createComparisonPredicate(fieldName, value, Predicate.Companion::greaterThan)
            ">=" -> createComparisonPredicate(fieldName, value, Predicate.Companion::greaterOrEqualThan)
            "<" -> createComparisonPredicate(fieldName, value, Predicate.Companion::lessThan)
            "<=" -> createComparisonPredicate(fieldName, value, Predicate.Companion::lessOrEqualThan)
            "~=" -> createAlmostEqualsPredicate(fieldName, value)
            else -> throw IllegalArgumentException("Unknown operator: $operator")
        }
    }

    private fun createEqualsPredicate(fieldName: String, value: String): Predicate {
        val schemaValue = parseValue(value)
        return Predicate.equals(fieldName, schemaValue)
    }

    private fun createAlmostEqualsPredicate(fieldName: String, value: String): Predicate {
        // Almost equals is for regex matching on a field
        val strValue = parseStringValue(value)
        return Predicate.almostEquals(fieldName, Schema.String(strValue))
    }

    private fun createComparisonPredicate(
        fieldName: String,
        value: String,
        factory: (String, Schema) -> Predicate
    ): Predicate {
        val schemaValue = parseValue(value)
        return factory(fieldName, schemaValue)
    }

    private fun parseValue(value: String): Schema {
        // Handle quoted strings
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return Schema.String(value.substring(1, value.length - 1))
        }

        // Handle numbers
        val number = value.toDoubleOrNull()
        if (number != null) {
            // Use Long for whole numbers, Double for decimals
            return if (number == number.toLong().toDouble()) {
                Schema.Long(number.toLong())
            } else {
                Schema.Double(number)
            }
        }

        // Handle booleans
        if (value.lowercase() == "true") return Schema.Boolean(true)
        if (value.lowercase() == "false") return Schema.Boolean(false)

        // Treat as unquoted string
        return Schema.String(value)
    }

    private fun parseStringValue(value: String): String {
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length - 1)
        }
        return value
    }

    /**
     * Simple tokenizer for predicate expressions.
     * Handles field access (.field), operators, strings, and numbers.
     */
    private class Tokenizer(source: String) {
        private val tokens = tokenize(source)
        private var pos = 0

        fun peek(): String? = tokens.getOrNull(pos)

        fun next(): String? {
            val token = peek()
            if (token != null) pos++
            return token
        }

        fun match(expected: String): Boolean {
            if (peek() == expected) {
                pos++
                return true
            }
            return false
        }

        fun consume(expected: String) {
            if (!match(expected)) {
                throw IllegalArgumentException("Expected '$expected' but got '${peek()}'")
            }
        }

        fun save(): Int = pos

        fun restore(saved: Int) {
            pos = saved
        }

        private fun tokenize(source: String): List<String> {
            val tokens = mutableListOf<String>()
            var i = 0

            while (i < source.length) {
                val c = source[i]

                when {
                    c.isWhitespace() -> i++

                    // Two-character operators
                    i + 1 < source.length && source.substring(i, i + 2) in listOf("==", "!=", ">=", "<=", "&&", "||", "~=") -> {
                        tokens.add(source.substring(i, i + 2))
                        i += 2
                    }

                    // Single-character operators and punctuation
                    c in listOf('>', '<', '(', ')', '!') -> {
                        tokens.add(c.toString())
                        i++
                    }

                    // Field access: .fieldName
                    c == '.' -> {
                        val start = i
                        i++ // skip the dot
                        while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) {
                            i++
                        }
                        tokens.add(source.substring(start, i))
                    }

                    // Quoted strings
                    c == '"' || c == '\'' -> {
                        val quote = c
                        val start = i
                        i++ // skip opening quote
                        while (i < source.length && source[i] != quote) {
                            if (source[i] == '\\' && i + 1 < source.length) {
                                i++ // skip escape char
                            }
                            i++
                        }
                        if (i < source.length) i++ // skip closing quote
                        tokens.add(source.substring(start, i))
                    }

                    // Numbers (including negative)
                    c.isDigit() || (c == '-' && i + 1 < source.length && source[i + 1].isDigit()) -> {
                        val start = i
                        if (c == '-') i++
                        while (i < source.length && (source[i].isDigit() || source[i] == '.')) {
                            i++
                        }
                        tokens.add(source.substring(start, i))
                    }

                    // Identifiers/barewords (for unquoted values)
                    c.isLetter() || c == '_' -> {
                        val start = i
                        while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) {
                            i++
                        }
                        tokens.add(source.substring(start, i))
                    }

                    else -> i++ // skip unknown characters
                }
            }

            return tokens
        }
    }
}
