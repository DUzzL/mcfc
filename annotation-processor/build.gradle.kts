plugins {
    kotlin("jvm")
}

group = "dev.epicpix"
version = "0.1.0-alpha.7"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup:javapoet:1.12.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.10-1.0.13")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}