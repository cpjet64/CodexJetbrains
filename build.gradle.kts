plugins {
  kotlin("jvm") version "1.9.24"
  id("org.jetbrains.intellij.platform") version "2.0.1"
}

// No intellijPlatform{} and no repositories{} here.
// Use raw coords for tests aligned by BOM.
val platformVersion = providers.gradleProperty("platformVersion").getOrElse("2024.1")

dependencies {
  testImplementation(platform("com.jetbrains.intellij.platform:intellij-platform-bom:$platformVersion"))
  testImplementation("com.jetbrains.intellij.platform:test-framework")
  // If needed:
  // testImplementation("com.jetbrains.intellij.platform:test-framework-core")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
  useJUnitPlatform()
  systemProperty("idea.platform.prefix", "Idea")
}
