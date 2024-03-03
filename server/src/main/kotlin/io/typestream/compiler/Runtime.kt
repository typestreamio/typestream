package io.typestream.compiler

import io.typestream.compiler.types.DataStream
import io.typestream.filesystem.FileSystem

data class Runtime(val name: String, val type: Type) {
    enum class Type {
        KAFKA,
        SHELL,
        MEMORY,
    }

    companion object {
        fun extractFrom(dataStream: DataStream): Runtime? {
            if (dataStream.path.startsWith(FileSystem.KAFKA_CLUSTERS_PREFIX)) {
                return Runtime(
                    dataStream.path.substring(FileSystem.KAFKA_CLUSTERS_PREFIX.length + 1).split("/").first(),
                    Type.KAFKA
                )
            }

            if (dataStream.path.startsWith(FileSystem.RANDOM_MOUNTS_PREFIX)) {
                return Runtime(
                    dataStream.path.substring(FileSystem.RANDOM_MOUNTS_PREFIX.length + 1).split("/").first(),
                    Type.MEMORY
                )
            }

            return null
        }
    }
}
