package dev.curt.codexjb.diff

data class Hunk(val header: String, val lines: List<String>)
data class FilePatch(val oldPath: String, val newPath: String, val hunks: List<Hunk>)

object UnifiedDiffParser {
    fun parse(text: String): List<FilePatch> {
        val lines = text.lineSequence().toList()
        val patches = mutableListOf<FilePatch>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("--- ") && i + 1 < lines.size && lines[i + 1].startsWith("+++ ")) {
                val oldPath = lines[i].substring(4).trim()
                val newPath = lines[i + 1].substring(4).trim()
                i += 2
                val hunks = mutableListOf<Hunk>()
                while (i < lines.size && lines[i].startsWith("@@")) {
                    val header = lines[i]
                    i += 1
                    val hunkLines = mutableListOf<String>()
                    while (i < lines.size && !lines[i].startsWith("@@") && !lines[i].startsWith("--- ")) {
                        hunkLines += lines[i]
                        i += 1
                    }
                    hunks += Hunk(header, hunkLines)
                }
                patches += FilePatch(oldPath, newPath, hunks)
            } else {
                i += 1
            }
        }
        return patches
    }
}

