package dev.epicpix.annotations.tests

import java.io.File

data class TestInfo(
    val id: String,
    val file: File,
) {
    fun javaFileName(): String {
        return "Test_${id.replace('/', '_')}"
    }
}
