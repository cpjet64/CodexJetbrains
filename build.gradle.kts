import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.intellij") version "1.17.2"
  kotlin("jvm") version "2.1.21"
}

group = "dev.curt"
version = "0.1.0"

repositories { mavenCentral() }

intellij {
  version.set("2025.2.2")
  type.set("IC")
  plugins.set(listOf("java"))
}

dependencies {
  compileOnly(kotlin("stdlib"))
  implementation("com.google.code.gson:gson:2.10.1")
  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit5"))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
      compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
  }
  buildSearchableOptions {
    enabled = false
  }

  patchPluginXml {
    sinceBuild.set("252")
    untilBuild.set("253.*")
  }

  test {
    useJUnitPlatform()
  }
}
