package dev.curt.codexjb.tooling

import dev.curt.codexjb.diff.Hunk

object PatchEngine {
    fun apply(original: String, hunks: List<Hunk>): String {
        val origLines = if (original.isEmpty()) emptyList() else original.split("\n")
        return applyLines(origLines, hunks)
    }

    fun applyLines(original: List<String>, hunks: List<Hunk>): String {
        val out = ArrayList<String>(original.size + 16)
        var srcIndex = 0
        for (h in hunks) {
            val oldStart = parseOldStart(h.header)
            val copyUntil = (oldStart - 1).coerceAtLeast(0)
            while (srcIndex < copyUntil && srcIndex < original.size) {
                out.add(original[srcIndex])
                srcIndex++
            }
            for (line in h.lines) {
                when {
                    line.startsWith(" ") -> {
                        val expected = line.substring(1)
                        if (srcIndex < original.size) {
                            val actual = original[srcIndex]
                            if (actual != expected) {
                                throw IllegalStateException("Context mismatch at line ${srcIndex + 1}")
                            }
                            out.add(actual)
                        }
                        srcIndex++
                    }
                    line.startsWith("-") -> {
                        val expected = line.substring(1)
                        if (srcIndex < original.size) {
                            val actual = original[srcIndex]
                            if (actual != expected) {
                                throw IllegalStateException("Deletion mismatch at line ${srcIndex + 1}")
                            }
                        }
                        srcIndex++
                    }
                    line.startsWith("+") -> out.add(line.substring(1))
                    else -> out.add(line)
                }
            }
        }
        while (srcIndex < original.size) {
            out.add(original[srcIndex])
            srcIndex++
        }
        return out.joinToString("\n")
    }

    private fun parseOldStart(header: String): Int {
        val re = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@")
        val m = re.find(header) ?: return 1
        return m.groupValues[1].toIntOrNull() ?: 1
    }
}

