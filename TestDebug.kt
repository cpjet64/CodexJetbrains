import dev.curt.codexjb.codelens.TodoParser

fun main() {
    val test1 = "/**\n * TODO: test\n */"
    println("Test 1 input: ${test1.replace("\n", "\\n")}")
    val result1 = TodoParser.parseTodos(test1)
    println("Test 1 result: Found ${result1.size} TODOs")
    result1.forEach { println("  - body='${it.body}', offset=${it.offset}") }

    val test2 = "// Java style TODO: one\n" +
            "# Python style TODO: two\n" +
            "-- SQL style TODO: three\n" +
            "/* C style TODO: four */"
    println("\nTest 2 input: ${test2.replace("\n", "\\n")}")
    val result2 = TodoParser.parseTodos(test2)
    println("Test 2 result: Found ${result2.size} TODOs")
    result2.forEach { println("  - body='${it.body}', offset=${it.offset}") }
}
