val ktor_version = "2.3.12"
val kotlin_version = "2.0.0"
val logback_version = "1.5.6"

plugins {
  application
  kotlin("jvm") version "2.0.0"
  id("io.ktor.plugin") version "2.3.12"
}

group = "com.paulmethfessel"
version = "1.0"

application {
  mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
  mavenCentral()
  maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

dependencies {
  implementation("io.ktor:ktor-client-core:$ktor_version")
  implementation("io.ktor:ktor-client-cio:$ktor_version")

  implementation("org.mnode.ical4j:ical4j:4.0.3")

  implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
  implementation("io.ktor:ktor-server-status-pages:$ktor_version")
  implementation("io.ktor:ktor-server-core-jvm")
  implementation("io.ktor:ktor-server-netty-jvm")
  implementation("ch.qos.logback:logback-classic:$logback_version")
  implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
  testImplementation("io.ktor:ktor-server-tests-jvm")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(17)
}