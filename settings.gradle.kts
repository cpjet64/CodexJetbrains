pluginManagement {
repositories {
gradlePluginPortal()
mavenCentral()
}
}
plugins {
// Required when managing repositories at the settings level for the IntelliJ Platform Gradle Plugin 2.x
id("org.jetbrains.intellij.platform.settings") version "2.9.0"
}
dependencyResolutionManagement {
repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
repositories {
mavenCentral()
// This call wires all official JetBrains repos, including IDE installers, platform releases, runtime, and Marketplace.
org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform.defaultRepositories()
}
}
rootProject.name = "codex-jetbrains-starter"
