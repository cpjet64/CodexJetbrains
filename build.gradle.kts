@file:Suppress("DEPRECATION")

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform")
}

group = "dev.curt"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id.set("dev.curt.codexjb")
        name.set("Codex (Unofficial)")
        ideaVersion {
            sinceBuild = "252"
            untilBuild = "252.*"
        }
    }
    pluginVerification {
        ides { recommended() }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    intellijPlatform {
        intellijIdeaCommunity("2025.2.2")
        bundledPlugin("com.intellij.java")
        // intellijIdeaCommunity("2025.2.2", useInstaller = false)
        // jetbrainsRuntime()
    }
}

tasks {
    named("buildSearchableOptions") { enabled = false }

    test {
        useJUnitPlatform()
    }

    withType<JavaExec>().configureEach {
        if (name == "runIde") {
            val home = System.getProperty("user.home")
            systemProperty("idea.system.path", "$home/.codex-idea-sandbox/system")
            systemProperty("idea.config.path", "$home/.codex-idea-sandbox/config")
            systemProperty("idea.plugins.path", "$home/.codex-idea-sandbox/plugins")
        }
    }
}
