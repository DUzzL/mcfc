plugins {
    kotlin("jvm") version "2.1.0"
}

group = "dev.epicpix"
version = project.properties.get("mod_version")!!

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.squareup:javapoet:1.12.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.0-1.0.28")
    implementation("cc.ekblad:4koma:1.2.0")
    implementation("org.ow2.asm:asm:9.6")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
    resources.srcDirs("../data")
}