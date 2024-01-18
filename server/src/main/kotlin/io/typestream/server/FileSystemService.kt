package io.typestream.server

import io.typestream.compiler.vm.Vm
import io.typestream.grpc.filesystem_service.FileSystemServiceGrpcKt
import io.typestream.grpc.filesystem_service.Filesystem.MountRequest
import io.typestream.grpc.filesystem_service.Filesystem.UnmountRequest
import io.typestream.grpc.filesystem_service.mountResponse
import io.typestream.grpc.filesystem_service.unmountResponse

class FileSystemService(private val vm: Vm) :
    FileSystemServiceGrpcKt.FileSystemServiceCoroutineImplBase() {

    override suspend fun mount(request: MountRequest) = mountResponse {
        vm.fileSystem.mount(request.config)
        success = true
    }

    override suspend fun unmount(request: UnmountRequest) = unmountResponse {
        vm.fileSystem.unmount(request.endpoint)

        success = true
    }
}
