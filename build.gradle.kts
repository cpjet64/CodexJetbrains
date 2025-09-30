@file:Suppress("DEPRECATION")
plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.0.20"
  id("org.jetbrains.intellij.platform")
}

group = "dev.curt"
version = "0.1.0"

// Java/Kotlin toolchains - align to Java 21 for 252-only support
java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// Kotlin toolchain is inferred from Java toolchain; explicit jvmToolchain not required

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
  // Ensure legacy JUnit 3/4 tests compile and run on JUnit Platform
  testImplementation("junit:junit:4.13.2")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

  intellijPlatform {
    // Pin to a real patch for deterministic resolution (installer artifacts by default)
    intellijIdeaCommunity("2025.2.2")
    // Java plugin APIs if used
    bundledPlugin("com.intellij.java")
    // If installer resolution fails, use ZIP and add JBR:
    // intellijIdeaCommunity("2025.2.2", useInstaller = false)
    // jetbrainsRuntime()
  }
}

tasks {
  // Disable generating searchable options to speed up local builds
  named("buildSearchableOptions") { enabled = false }

  test {
    useJUnitPlatform()
  }

  // Ensure sandbox paths are located on a native Linux filesystem (helps WSL users).
  // This avoids Unix domain socket errors (e.g., DirectoryLock) when the project
  // resides on a Windows-mounted drive like /mnt/c.
  withType<JavaExec>().configureEach {
    if (name == "runIde") {
      val home = System.getProperty("user.home")
      systemProperty("idea.system.path", "$home/.codex-idea-sandbox/system")
      systemProperty("idea.config.path", "$home/.codex-idea-sandbox/config")
      systemProperty("idea.plugins.path", "$home/.codex-idea-sandbox/plugins")
    }
  }
}

