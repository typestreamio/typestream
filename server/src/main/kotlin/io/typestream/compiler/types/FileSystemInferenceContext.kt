package io.typestream.compiler.types

import io.typestream.filesystem.FileSystem

/**
 * InferenceContext implementation backed by the FileSystem catalog.
 * Used by GraphCompiler to provide catalog access during schema inference.
 */
class FileSystemInferenceContext(private val fileSystem: FileSystem) : InferenceContext {

    override fun lookupDataStream(path: String): DataStream {
        return fileSystem.findDataStream(path)
            ?: error("No DataStream for path: $path")
    }

    override fun lookupEncoding(path: String): Encoding {
        return fileSystem.inferEncodingForPath(path)
    }
}
