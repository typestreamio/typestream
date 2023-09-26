package io.typestream.server

import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.compiler.vm.Vm
import io.typestream.filesystem.FileSystem
import io.typestream.grpc.job_service.Job.CreateJobRequest
import io.typestream.grpc.job_service.JobServiceGrpcKt
import io.typestream.grpc.job_service.createJobResponse
import io.typestream.k8s.K8sClient
import io.typestream.scheduler.Scheduler
import java.util.UUID

class JobService(private val systemVersion: String, private val k8sMode: Boolean, private val vm: Vm) :
    JobServiceGrpcKt.JobServiceCoroutineImplBase() {

    override suspend fun createJob(request: CreateJobRequest) = createJobResponse {
        //TODO we may want to generate uuids from the source code fingerprint
        // as it is, we'll have running apps on runtime (e.g. kafka streams) with a different id than the
        // job id that we create here
        val id = "typestream-app-${UUID.randomUUID()}"

        if (k8sMode) {
            K8sClient().use {
                it.createWorkerJob(systemVersion, id, request.source)
            }
        } else {
            vm.run(request.source, Session(vm.fileSystem, vm.scheduler, Env()))
        }

        this.jobId = id
    }
}
