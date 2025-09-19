plugins {
  id("org.jetbrains.intellij") version "1.17.2"
  kotlin("jvm") version "1.9.24"
}

group = "dev.curt"
version = "0.1.0"

repositories { mavenCentral() }

intellij {
  version.set("2023.3")
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
    kotlinOptions.jvmTarget = "17"
  }
  buildSearchableOptions {
    enabled = false
  }

  patchPluginXml {
    sinceBuild.set("233")
    untilBuild.set("242.*")
  }

  test {
    useJUnitPlatform()
  }
}
