package dev.epicpix.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InstructionSource

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InstructionDestination

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class InstructionSourceClass(val enumClass: String, val generatorClass: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class MapFieldUse(val descriptor: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class MapFunctionUse(val descriptor: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class MapSourceClass(val value: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FILE)
annotation class ConstantsFile(val value: String)
