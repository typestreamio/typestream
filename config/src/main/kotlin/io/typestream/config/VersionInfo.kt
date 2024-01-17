package io.typestream.config

data class VersionInfo(val version: String, val commitHash: String) {
    companion object {
        fun fetch(): VersionInfo {
            val version = VersionInfo::class.java.getResourceAsStream("/version-info.properties")
            val properties = java.util.Properties()
            properties.load(version)
            return VersionInfo(properties.getProperty("version"), properties.getProperty("commitHash"))
        }
    }

    override fun toString() = "$version ($commitHash)"
}
