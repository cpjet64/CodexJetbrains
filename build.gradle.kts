// build.gradle.kts
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
  kotlin("jvm") version "1.9.24"
  id("org.jetbrains.intellij.platform") // version provided by settings
}

group = "dev.curt"
version = "0.1.0"

// --- JDK 21 everywhere ---
java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
kotlin {
  // makes Gradle pick JDK 21 toolchain for Kotlin compilation
  jvmToolchain(21)
}
// old-style options still fine on Kotlin 1.9.x
tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = "21"
}

// Parameterize target IDE version and whether we need Java PSI
val platformVersion = providers.gradleProperty("platformVersion").getOrElse("2024.2.1")
val withJava = providers.gradleProperty("codex_withJava").map { it.toBoolean() }.getOrElse(false)

// Configure IntelliJ Platform plugin
intellijPlatform {
  pluginConfiguration {
    version = project.version.toString()

    // Configure IDE version compatibility
    ideaVersion {
      sinceBuild = "242"  // 2024.2
      untilBuild = provider { null }  // Open-ended for now
    }
  }

  // Plugin verification settings
  pluginVerification {
    ides {
      // Verify against the target platform version
      // Use recommended() when closer to release
      select {
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        channels = listOf(ProductRelease.Channel.RELEASE)
        sinceBuild = "242"
        untilBuild = "242.*"
      }
    }
  }
}

dependencies {
  intellijPlatform {
    // Choose a baseline product. Using Community gives you broadest surface.
    intellijIdeaCommunity(platformVersion)

    // Add Platform test framework (includes JUnit4 by default for compatibility)
    testFramework(TestFrameworkType.Platform)

    // Add Git4Idea for git integration support
    bundledPlugin("Git4Idea")

    // Toggle Java plugin (limits to IDEs that actually ship it: IDEA, Android Studio, etc.)
    if (withJava) {
      bundledPlugin("com.intellij.java")
    }
  }

  // Add JUnit4 for tests that use JUnit4 annotations
  testImplementation("junit:junit:4.13.2")

  // Add JUnit5 for tests that use JUnit5 annotations (jupiter)
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1") // For JUnit4 compatibility

  // Workaround for IJPL-157292: opentest4j dependency not resolved with TestFrameworkType.Platform
  testImplementation("org.opentest4j:opentest4j:1.3.0")

  // Your Kotlin tests
  testImplementation(kotlin("test"))
}

tasks.test {
  // Tests use both JUnit4 and JUnit5 (vintage engine provides JUnit4 support)
  useJUnitPlatform()
  // Needed by some tests to satisfy platform prefix checks
  systemProperty("idea.platform.prefix", "Idea")
}

// Configure tasks
tasks {
  // Custom sandbox location for runIde
  runIde {
    val home = System.getProperty("user.home")
    systemProperty("idea.system.path", "$home/.codex-idea-sandbox/system")
    systemProperty("idea.config.path", "$home/.codex-idea-sandbox/config")
    systemProperty("idea.plugins.path", "$home/.codex-idea-sandbox/plugins")

    // Suppress native launcher notification on Windows
    systemProperty("idea.native.launcher.notification.suppressed", "true")
  }

  // Enable buildSearchableOptions for production builds
  // This allows users to search for plugin settings in IDE preferences
  buildSearchableOptions {
    enabled = true
  }

  prepareJarSearchableOptions {
    enabled = true
  }

  jarSearchableOptions {
    enabled = true
  }

  // Configure verification
  verifyPlugin {
    // Will use the ides configuration from intellijPlatform.pluginVerification
  }

  // Sign the plugin if signing is configured
  signPlugin {
    // Signing is optional - configure via environment variables or gradle.properties
    // See: https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
  }

  // Publish plugin - configure token via PUBLISH_TOKEN environment variable
  publishPlugin {
    // Token from environment variable or gradle.properties
    // See: https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html
  }
}

// DO NOT declare repositories here; they're in settings.gradle.kts
