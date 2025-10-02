import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.jvm") version "1.9.24"
  id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "dev.curt"
version = "0.1.0"

// Gradle Java toolchain -> build runs with JDK 21
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

// Kotlin toolchain + bytecode target 21 (no more accidental 1.8 targets)
kotlin {
  jvmToolchain(21)
}
tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = "21"
}

// Toggle this to broaden/narrow IDE coverage.
// true  = include Java plugin (IDEA & friends)
// false = platform-only (broader set of IDEs)
val includeJavaPlugin = (providers.gradleProperty("codex.withJava").orNull ?: "true").toBoolean()

dependencies {
  // Choose an IDE baseline that runs on JBR 21.
  // 242 = 2024.2, 243 = 2024.3, 251 = 2025.1, 252 = 2025.2
  intellijPlatform {
    // Community is fine up to 2025.2; from 2025.3 IC is consolidated (you'd switch to intellijIdea()).
    intellijIdeaCommunity("2024.2")
    if (includeJavaPlugin) {
      bundledPlugin("com.intellij.java")
    }
    // instrumentationTools() is unnecessary in 2.x (plugin warns if you call it)
  }

  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
  systemProperty("idea.platform.prefix", "Idea")
}

// DO NOT configure runIde { autoReloadPlugins = ... } in 2.x — that property was removed.
// Dynamic plugin reload is handled by the IDE itself in supported cases.
