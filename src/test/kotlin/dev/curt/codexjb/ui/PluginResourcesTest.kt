package dev.curt.codexjb.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginResourcesTest {

    @Test
    fun pluginIconsArePresentAndNonEmpty() {
        val icon = Path.of("src", "main", "resources", "META-INF", "pluginIcon.svg")
        val iconDark = Path.of("src", "main", "resources", "META-INF", "pluginIcon_dark.svg")

        assertTrue(Files.exists(icon), "pluginIcon.svg must exist")
        assertTrue(Files.size(icon) > 0, "pluginIcon.svg must be non-empty")
        assertTrue(Files.exists(iconDark), "pluginIcon_dark.svg must exist")
        assertTrue(Files.size(iconDark) > 0, "pluginIcon_dark.svg must be non-empty")
    }
}
