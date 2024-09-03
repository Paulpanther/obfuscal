val ktor_version = "2.3.12"
val kotlin_version = "2.0.0"
val logback_version = "1.5.6"
val exposed_version = "0.53.0"

plugins {
  application
  kotlin("jvm") version "2.0.0"
  id("io.ktor.plugin") version "2.3.12"
  kotlin("plugin.serialization") version "2.0.20"
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
  // Logger
  implementation("org.apache.logging.log4j:log4j-core:2.23.1")
  implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.23.1")

  // DB & ORM
  implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
  implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
  implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
  implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
  implementation("org.xerial:sqlite-jdbc:3.46.1.0")

  // Calendar Parser/Writer
  implementation("org.mnode.ical4j:ical4j:4.0.3")

  // Ktor Client
  implementation("io.ktor:ktor-client-core:$ktor_version")
  implementation("io.ktor:ktor-client-cio:$ktor_version")

  // Ktor Server
  implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
  implementation("io.ktor:ktor-server-status-pages:$ktor_version")
  implementation("io.ktor:ktor-server-core-jvm")
  implementation("io.ktor:ktor-server-netty-jvm")
  implementation("ch.qos.logback:logback-classic:$logback_version")
  implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
  implementation("io.ktor:ktor-server-auth:$ktor_version")


  // testing
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(17)
}