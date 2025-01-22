plugins {
    kotlin("jvm") version "1.9.21"
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
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.21-1.0.16")
    implementation("cc.ekblad:4koma:1.2.0")
    implementation("net.fabricmc:tiny-remapper:0.8.2")
    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("org.ow2.asm:asm:9.6")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
    java.srcDirs("../data")
}