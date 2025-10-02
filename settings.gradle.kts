import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-repository/snapshots")
  }
}

plugins {
  // Settings plugin is REQUIRED only if you use dependencyResolutionManagement here.
  id("org.jetbrains.intellij.platform.settings") version "2.9.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    // 2.x settings extension – this is valid because we applied the settings plugin above:
    intellijPlatform { defaultRepositories() }
  }
}

rootProject.name = "CodexJetbrains"
