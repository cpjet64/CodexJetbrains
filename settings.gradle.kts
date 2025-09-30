pluginManagement {
repositories {
gradlePluginPortal()
mavenCentral()
}
plugins {
  // Manage the platform plugin version at settings level to avoid on-classpath ambiguity
  id("org.jetbrains.intellij.platform") version "2.9.0"
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
  // Official JetBrains repositories (installers, platform, runtime, marketplace)
  intellijPlatform {
    defaultRepositories()
  }
}
}
rootProject.name = "codex-jetbrains-starter"
