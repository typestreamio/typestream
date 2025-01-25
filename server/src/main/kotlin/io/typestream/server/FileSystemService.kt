package io.typestream.server

import io.typestream.compiler.vm.Vm
import io.typestream.grpc.filesystem_service.FileSystemServiceGrpcKt
import io.typestream.grpc.filesystem_service.Filesystem
import io.typestream.grpc.filesystem_service.Filesystem.MountRequest
import io.typestream.grpc.filesystem_service.Filesystem.UnmountRequest
import io.typestream.grpc.filesystem_service.lsResponse
import io.typestream.grpc.filesystem_service.mountResponse
import io.typestream.grpc.filesystem_service.unmountResponse

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
        vm.fileSystem.ls(request.path).forEach { files += it }
    }
}
