package dev.curt.codexjb.core

object SensitiveDataRedactor {
    private val sensitiveKeyMarkers = listOf(
        "TOKEN",
        "SECRET",
        "PASSWORD",
        "CREDENTIAL",
        "ACCESS_TOKEN",
        "ACCESS_KEY",
        "PRIVATE_KEY",
        "SESSION_KEY",
        "API_KEY",
        "CLIENT_SECRET",
        "AUTH_TOKEN"
    )

    private val markersPattern = sensitiveKeyMarkers.joinToString("|") { Regex.escape(it) }
    private val jsonEntryRegex = Regex("""("([A-Za-z0-9_\-]+)"\s*:\s*")([^"]*)(")""")
    private val envVarRegex = Regex(
        """\b([A-Za-z0-9_]*?(?:$markersPattern)[A-Za-z0-9_]*)=([^\s"]+)""",
        RegexOption.IGNORE_CASE
    )
    private val bearerTokenRegex = Regex("""(?i)(bearer\s+)([A-Za-z0-9\-_\.]{20,})""")
    private val directTokenRegexes = listOf(
        Regex("""gh[pous]_[A-Za-z0-9]{6,}""", RegexOption.IGNORE_CASE),
        Regex("""github_pat_[A-Za-z0-9_]{10,}""", RegexOption.IGNORE_CASE),
        Regex("""sk-[A-Za-z0-9]{16,}""")
    )

    fun redact(line: String): String {
        if (line.isEmpty()) return line
        var result = jsonEntryRegex.replace(line) { match ->
            val key = match.groupValues[2]
            if (isSensitiveKey(key)) "\"$key\": \"[REDACTED]\"" else match.value
        }
        result = envVarRegex.replace(result) { match ->
            val key = match.groupValues[1]
            "$key=[REDACTED]"
        }
        result = bearerTokenRegex.replace(result) { match ->
            "${match.groupValues[1]}[REDACTED]"
        }
        directTokenRegexes.forEach { regex ->
            result = regex.replace(result, "[REDACTED]")
        }
        return result
    }

    private fun isSensitiveKey(key: String): Boolean {
        val upper = key.uppercase()
        return sensitiveKeyMarkers.any { marker -> upper.contains(marker) }
    }
}
