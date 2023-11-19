plugins {
    kotlin("jvm")
}

group = "dev.epicpix"
version = project.properties.get("mod_version")!!

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
}

dependencies {
    implementation("com.squareup:javapoet:1.12.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.10-1.0.13")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.fabricmc:tiny-remapper:0.8.2")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}