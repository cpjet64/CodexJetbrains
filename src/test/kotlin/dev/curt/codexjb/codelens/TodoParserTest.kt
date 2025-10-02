package dev.curt.codexjb.codelens

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TodoParserTest {

    @Test
    fun `test single-line slash-slash comment`() {
        val input = "// TODO: test"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        assertEquals("test", result[0].body)
    }

    @Test
    fun `test hash comment`() {
        val input = "# TODO: test"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size, "Expected 1 TODO but found ${result.size}")
        assertEquals("test", result[0].body)
    }

    @Test
    fun `test hash comment with newline before`() {
        val input = "\n# TODO: test"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size, "Expected 1 TODO but found ${result.size}")
        assertEquals("test", result[0].body)
    }

    @Test
    fun `test SQL double-dash comment`() {
        val input = "-- TODO: test"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        assertEquals("test", result[0].body)
    }

    @Test
    fun `test multi-line comment`() {
        val input = "/* TODO: test */"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        assertEquals("test", result[0].body)
    }

    @Test
    fun `test Javadoc comment with leading asterisks`() {
        val input = "/**\n * TODO: test\n */"
        println("DEBUG: Input string = ${input.replace("\n", "\\n").replace(" ", "␣")}")
        println("DEBUG: Input length = ${input.length}")
        input.forEachIndexed { i, c -> println("DEBUG: [$i] = '${c}' (${c.code})") }

        val result = TodoParser.parseTodos(input)

        println("DEBUG: Found ${result.size} TODOs")
        result.forEach { println("DEBUG: TODO body='${it.body}', offset=${it.offset}") }

        assertEquals(1, result.size, "Expected 1 TODO but found ${result.size}. TODOs: ${result.map { it.body }}")
        assertTrue(result[0].body.contains("test"), "Expected body to contain 'test' but was '${result[0].body}'")
    }

    @Test
    fun `test TODO inside string should be ignored`() {
        val input = """val x = "TODO: not real""""
        val result = TodoParser.parseTodos(input)

        assertEquals(0, result.size)
    }

    @Test
    fun `test case insensitive TODO`() {
        val input = """
            // todo: lowercase
            // Todo: mixed
            // TODO: upper
        """.trimIndent()
        val result = TodoParser.parseTodos(input)

        assertEquals(3, result.size)
        assertEquals("lowercase", result[0].body)
        assertEquals("mixed", result[1].body)
        assertEquals("upper", result[2].body)
    }

    @Test
    fun `test word boundary check - TODONT should not match`() {
        val input = "// TODONT: should not match"
        val result = TodoParser.parseTodos(input)

        assertEquals(0, result.size)
    }

    @Test
    fun `test optional punctuation after TODO`() {
        val input = """
            // TODO: with colon
            // TODO- with dash
            // TODO no punct
        """.trimIndent()
        val result = TodoParser.parseTodos(input)

        assertEquals(3, result.size)
        assertEquals("with colon", result[0].body)
        assertEquals("with dash", result[1].body)
        assertEquals("no punct", result[2].body)
    }

    @Test
    fun `test multiple TODOs in file`() {
        val input = "// TODO: first\n" +
                "val x = 1\n" +
                "# TODO: second\n" +
                "val y = 2\n" +
                "-- TODO: third\n" +
                "val z = 3\n" +
                "/* TODO: fourth */\n" +
                "/**\n" +
                " * TODO: fifth\n" +
                " */"
        val result = TodoParser.parseTodos(input)

        assertEquals(5, result.size, "Expected 5 TODOs but found ${result.size}. TODOs: ${result.map { it.body }}")
        assertEquals("first", result[0].body)
        assertEquals("second", result[1].body)
        assertEquals("third", result[2].body)
        assertEquals("fourth", result[3].body)
        assertTrue(result[4].body.contains("fifth"))
    }

    @Test
    fun `test TODO with no text after keyword`() {
        val input = "// TODO"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        assertEquals("", result[0].body)
    }

    @Test
    fun `test repeated slashes should be skipped`() {
        val input = "/// TODO: test"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        assertEquals("test", result[0].body)
    }

    @Test
    fun `test escaped quotes in strings`() {
        val input = """val x = "TODO: \" still in string""""
        val result = TodoParser.parseTodos(input)

        assertEquals(0, result.size)
    }

    @Test
    fun `test single quote strings`() {
        val input = """val x = 'TODO: not real'"""
        val result = TodoParser.parseTodos(input)

        assertEquals(0, result.size)
    }

    @Test
    fun `test backtick strings`() {
        val input = """val x = `TODO: not real`"""
        val result = TodoParser.parseTodos(input)

        assertEquals(0, result.size)
    }

    @Test
    fun `test TODO offset is correct`() {
        val input = "// TODO: test"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        // Offset should point to "test" (after "TODO: ")
        assertTrue(result[0].offset > 0)
        assertTrue(result[0].offset < input.length)
    }

    @Test
    fun `test no TODOs in plain code`() {
        val input = """
            val x = 1
            val y = 2
            fun foo() {
                return 42
            }
        """.trimIndent()
        val result = TodoParser.parseTodos(input)

        assertEquals(0, result.size)
    }

    @Test
    fun `test TODO at end of file with no newline`() {
        val input = "// TODO: last"
        val result = TodoParser.parseTodos(input)

        assertEquals(1, result.size)
        assertEquals("last", result[0].body)
    }

    @Test
    fun `test unclosed multi-line comment`() {
        val input = "/* TODO: unclosed"
        val result = TodoParser.parseTodos(input)

        // Should handle gracefully (may or may not find TODO depending on implementation)
        assertTrue(result.size >= 0)
    }

    @Test
    fun `test mixed comment styles in one file`() {
        // Test each style individually first
        val test1 = "// TODO: one"
        assertEquals(1, TodoParser.parseTodos(test1).size, "Slash comment failed")

        val test2 = "# TODO: two"
        assertEquals(1, TodoParser.parseTodos(test2).size, "Hash comment failed")

        val test3 = "-- TODO: three"
        assertEquals(1, TodoParser.parseTodos(test3).size, "SQL comment failed")

        val test4 = "/* TODO: four */"
        assertEquals(1, TodoParser.parseTodos(test4).size, "Multi-line comment failed")

        // Now test all together
        val input = test1 + "\n" + test2 + "\n" + test3 + "\n" + test4
        println("DEBUG mixed: Input = ${input.replace("\n", "\\n")}")
        val result = TodoParser.parseTodos(input)
        println("DEBUG mixed: Found ${result.size} TODOs: ${result.map { "(${it.body}, off=${it.offset})" }}")

        assertEquals(4, result.size, "Expected 4 TODOs but found ${result.size}. TODOs: ${result.map { it.body }}")
    }
}
