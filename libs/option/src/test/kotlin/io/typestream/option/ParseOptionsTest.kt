package io.typestream.option

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


data class GrepOptions(
    @param:Option(names = ["-v", "--invert-match"], description = "inverts match") val invert: Boolean,
    @param:Option(names = ["-k", "--by-key"], description = "greps the key") val byKey: Boolean,
    @param:Option(names = ["-A", "--after"], description = "prints N lines after the patter") val after: Int,
    @param:Option(names = ["-p", "--path"], description = "path") val path: String,
)

internal class ParseOptionsTest {

    @Test
    fun `parses boolean options presence`() {
        val (shortGrepOptions, shortArgs) = parseOptions<GrepOptions>(listOf("-v", "answer", "42"))

        assertTrue(shortGrepOptions.invert)
        assertEquals(listOf("answer", "42"), shortArgs)

        val (longGrepOptions, longArgs) = parseOptions<GrepOptions>(listOf("--invert-match", "answer", "42"))

        assertTrue(longGrepOptions.invert)
        assertEquals(listOf("answer", "42"), longArgs)
    }

    @Test
    fun `parses more than one boolean option`() {
        val (shortGrepOptions, shortArgs) = parseOptions<GrepOptions>(listOf("-v", "answer", "42", "-k"))

        assertTrue(shortGrepOptions.invert)
        assertTrue(shortGrepOptions.byKey)
        assertEquals(listOf("answer", "42"), shortArgs)
    }

    @Test
    fun `parses boolean options absence`() {
        val (shortGrepOptions, shortArgs) = parseOptions<GrepOptions>(listOf("answer", "42"))

        assertFalse(shortGrepOptions.invert)
        assertEquals(listOf("answer", "42"), shortArgs)
    }

    @Test
    fun `parses params`() {
        val (grepOptionsWithParams, shortArgs) = parseOptions<GrepOptions>(listOf("-A", "24", "-v", "answer", "42"))

        assertTrue(grepOptionsWithParams.invert)
        assertEquals(24, grepOptionsWithParams.after)
        assertEquals(listOf("answer", "42"), shortArgs)
    }

    @Test
    fun `parses string option`() {
        val (shortGrepOptions, shortArgs) = parseOptions<GrepOptions>(listOf("-p", "/dev/null", "answer", "42"))

        assertEquals(shortGrepOptions.path, "/dev/null")
        assertEquals(listOf("answer", "42"), shortArgs)
    }
}
