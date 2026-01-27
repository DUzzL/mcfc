plugins {
    kotlin("jvm") version "2.3.0"
}

group = "dev.epicpix"
version = project.properties["mod_version"]!!

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.squareup:javapoet:1.12.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.4")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.ow2.asm:asm:9.9")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
    resources.srcDirs("../data")
}