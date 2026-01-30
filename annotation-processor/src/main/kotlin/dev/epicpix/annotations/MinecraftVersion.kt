package dev.epicpix.annotations

data class MinecraftVersion(val year: Int, val drop: Int, val hotfix: Int) {
    companion object {
        fun parse(versionString: String): MinecraftVersion {
            val baseVersion = versionString.split('-')[0]
            val baseNumbers = baseVersion.split('.')

            val yearString = baseNumbers.getOrNull(0) ?: throw IllegalArgumentException("Version missing year: $versionString")
            val dropString = baseNumbers.getOrNull(1) ?: throw IllegalArgumentException("Version missing drop: $versionString")
            val hotfixString = baseNumbers.getOrElse(2) { "0" }

            val year = yearString.toIntOrNull() ?: throw IllegalArgumentException("Version year is not an integer")
            val drop = dropString.toIntOrNull() ?: throw IllegalArgumentException("Version drop is not an integer")
            val hotfix = hotfixString.toIntOrNull() ?: throw IllegalArgumentException("Version hotfix is not an integer")

            return MinecraftVersion(year, drop, hotfix)
        }
    }

    operator fun compareTo(other: MinecraftVersion): Int {
        if(year != other.year) return year.compareTo(other.year)
        if(drop != other.drop) return drop.compareTo(other.drop)
        return hotfix.compareTo(other.hotfix)
    }

}
