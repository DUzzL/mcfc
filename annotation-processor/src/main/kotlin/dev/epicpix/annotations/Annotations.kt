package dev.epicpix.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class InstructionSourceClass(val enumClass: String, val generatorClass: String)
