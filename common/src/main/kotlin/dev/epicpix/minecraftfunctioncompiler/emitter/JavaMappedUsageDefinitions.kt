package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.annotations.MapFunctionUse
import dev.epicpix.annotations.MapSourceClass

@MapSourceClass("dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageInitializer")
object JavaMappedUsageDefinitions {

    @MapFunctionUse("java/util/ArrayList:size()I")
    var ArrayList_size: MappedFunctionData? = null
    @MapFunctionUse("java/util/ArrayList:get(I)Ljava/lang/Object;")
    var ArrayList_get: MappedFunctionData? = null
    @MapFunctionUse("java/util/List:size()I")
    var List_size: MappedFunctionData? = null
    @MapFunctionUse("java/util/List:get(I)Ljava/lang/Object;")
    var List_get: MappedFunctionData? = null
    @MapFunctionUse("java/util/Set:size()I")
    var Set_size: MappedFunctionData? = null
    @MapFunctionUse("java/util/Set:contains(Ljava/lang/Object;)Z")
    var Set_contains: MappedFunctionData? = null
    @MapFunctionUse("java/util/Map:keySet()Ljava/util/Set;")
    var Map_keySet: MappedFunctionData? = null
    @MapFunctionUse("java/util/Map:get(Ljava/lang/Object;)Ljava/lang/Object;")
    var Map_get: MappedFunctionData? = null
    @MapFunctionUse("java/util/Iterator:next()Ljava/lang/Object;")
    var Iterator_next: MappedFunctionData? = null
    @MapFunctionUse("java/util/Iterator:hasNext()Z")
    var Iterator_hasNext: MappedFunctionData? = null
    @MapFunctionUse("java/lang/Iterable:iterator()Ljava/util/Iterator;")
    var Iterable_iterator: MappedFunctionData? = null

    @MapFunctionUse("java/lang/Math:floor(D)D")
    var Math_floor: MappedFunctionData? = null
    @MapFunctionUse("java/lang/Math:floorDiv(II)I")
    var Math_floorDiv: MappedFunctionData? = null
    @MapFunctionUse("java/lang/Math:floorMod(II)I")
    var Math_floorMod: MappedFunctionData? = null
    @MapFunctionUse("java/lang/Math:min(II)I")
    var Math_min: MappedFunctionData? = null
    @MapFunctionUse("java/lang/Math:max(II)I")
    var Math_max: MappedFunctionData? = null

    @MapFunctionUse("java/lang/String:equals(Ljava/lang/Object;)Z")
    var String_equals: MappedFunctionData? = null

    init { JavaMappedUsageInitializer.init() }
}