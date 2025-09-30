@file:Suppress("UnstableApiUsage")
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Required to use intellijPlatform { defaultRepositories() } at settings level
    id("org.jetbrains.intellij.platform.settings") version "2.9.0"
}

dependencyResolutionManagement {
    // Use setter form; keeps IDE happy and is the canonical style
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Official JetBrains repositories (installers, releases, Marketplace, JBR)
        intellijPlatform { defaultRepositories() }
    }
}

rootProject.name = "codex-jetbrains-starter"
