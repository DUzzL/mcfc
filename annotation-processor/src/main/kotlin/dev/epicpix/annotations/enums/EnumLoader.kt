package dev.epicpix.annotations.enums

data class EnumList(val enums: Map<String, String>) {
    fun getEnumMappings(from: String, to: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for((k, v) in enums) {
            if(k.startsWith(from) && v.startsWith(to)) {
                result.add(k.substring(from.length + 1) to v)
            }
        }
        return result
    }
}

fun parseEnums(fileContent: String): EnumList {
    val enums = mutableMapOf<String, String>()
    for(line in fileContent.lineSequence()) {
        if(line.isBlank()) continue
        val parts = line.split(" -> ", limit = 2)
        enums[parts[0]] = parts[1]
        enums[parts[1]] = parts[0]
    }
    return EnumList(enums)
}