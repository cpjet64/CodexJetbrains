package dev.curt.codexjb.core

import com.intellij.openapi.components.Service
import java.nio.file.Path

@Service
class CodexConfigService {
    var cliPath: Path? = null
    var defaultArgs: List<String> = listOf("proto")

    @Volatile
    private var discoverer: (Path?) -> Path? = { wd ->
        CodexPathDiscovery.discover(
            os = CodexPathDiscovery.currentOs(),
            env = System.getenv(),
            workingDirectory = wd
        )
    }

    fun installDiscoverer(fn: (Path?) -> Path?) { // for tests
        discoverer = fn
    }

    fun resolveExecutable(workingDirectory: Path?): Path? {
        return cliPath ?: discoverer(workingDirectory)
    }
}

