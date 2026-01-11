package io.typestream.server

import io.typestream.compiler.types.Encoding
import io.typestream.compiler.vm.Vm
import io.typestream.grpc.filesystem_service.FileSystemServiceGrpcKt
import io.typestream.grpc.filesystem_service.Filesystem
import io.typestream.grpc.filesystem_service.Filesystem.MountRequest
import io.typestream.grpc.filesystem_service.Filesystem.UnmountRequest
import io.typestream.compiler.types.schema.Schema
import io.typestream.grpc.filesystem_service.fileInfo
import io.typestream.grpc.filesystem_service.getSchemaResponse
import io.typestream.grpc.filesystem_service.lsResponse
import io.typestream.grpc.filesystem_service.mountResponse
import io.typestream.grpc.filesystem_service.unmountResponse
import io.typestream.grpc.job_service.Job

class FileSystemService(private val vm: Vm) :
    FileSystemServiceGrpcKt.FileSystemServiceCoroutineImplBase() {

    override suspend fun mount(request: MountRequest): Filesystem.MountResponse = mountResponse {
        vm.fileSystem.mount(request.config)
        success = true
    }

    override suspend fun unmount(request: UnmountRequest): Filesystem.UnmountResponse = unmountResponse {
        vm.fileSystem.unmount(request.endpoint)

        success = true
    }

    override suspend fun ls(request: Filesystem.LsRequest): Filesystem.LsResponse = lsResponse {
        val basePath = request.path.removeSuffix("/")
        vm.fileSystem.ls(request.path).forEach { fileName ->
            val fullPath = if (basePath == "/") "/$fileName" else "$basePath/$fileName"
            val encoding = vm.fileSystem.findEncodingForPath(fullPath)
            files += fileInfo {
                name = fileName
                this.encoding = encoding?.toProtoEncoding() ?: Job.Encoding.STRING
            }
        }
    }

    override suspend fun getSchema(request: Filesystem.GetSchemaRequest): Filesystem.GetSchemaResponse =
        getSchemaResponse {
            try {
                val dataStream = vm.fileSystem.findDataStream(request.path)
                if (dataStream == null) {
                    error = "Topic not found: ${request.path}"
                } else {
                    val schema = dataStream.schema
                    if (schema is Schema.Struct) {
                        fields += schema.value.map { it.name }
                    } else {
                        error = "Schema is not a struct type"
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
            }
        }
}

private fun Encoding.toProtoEncoding(): Job.Encoding = when (this) {
    Encoding.STRING -> Job.Encoding.STRING
    Encoding.NUMBER -> Job.Encoding.NUMBER
    Encoding.JSON -> Job.Encoding.JSON
    Encoding.AVRO -> Job.Encoding.AVRO
    Encoding.PROTOBUF -> Job.Encoding.PROTOBUF
}
