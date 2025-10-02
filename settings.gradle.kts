// settings.gradle.kts
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
  plugins {
    // keep these in sync
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("org.jetbrains.intellij.platform.settings") version "2.9.0"
  }
}

// MUST be after pluginManagement
plugins {
  id("org.jetbrains.intellij.platform.settings")
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
  }
}

rootProject.name = "CodexJetbrains"

// Disable build cache to always do fresh builds
// This prevents stale cache issues during development
buildCache {
  local {
    isEnabled = false
  }
}
