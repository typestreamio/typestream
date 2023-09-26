package io.typestream.k8s

import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader

class K8sClient : Closeable {
    private val kubernetesClient = KubernetesClientBuilder()
        .withConfig(
            ConfigBuilder()
                .withConnectionTimeout(100)
                .withRequestTimeout(100)
                .build()
        ).build()

    fun getServerProperties(): InputStream? = kubernetesClient.configMaps()
        .inNamespace("typestream")
        .withName("server-config")
        .get().data["server.properties"]?.byteInputStream()

    override fun close() {
        kubernetesClient.close()
    }

    fun getJobs(): List<Job> {
        val jobList = kubernetesClient.batch().v1().jobs().inNamespace("typestream").list()

        return jobList.items.map { job ->
            Job(
                job.metadata.name,
                when (job.status.active) {
                    1 -> Job.State.RUNNING
                    0 -> Job.State.STOPPED
                    else -> Job.State.UNKNOWN
                }
            )
        }
    }

    fun createWorkerJob(typestreamVersion: String, workerId: String, payload: String): Job {
        val image = if (typestreamVersion == "beta") {
            "localhost:5000/typestream/server:beta"
        } else {
            "typestream/server:$typestreamVersion"
        }

        val job = kubernetesClient.batch().v1().jobs().inNamespace("typestream").resource(
            JobBuilder()
                .withNewMetadata()
                .withName("worker-$workerId")
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withServiceAccount("typestream-service-account")
                .addNewContainer()
                .withName("worker")
                .withImage(image)
                .withImagePullPolicy("Always")
                .withEnv(
                    EnvVarBuilder()
                        .withName("WORKER_ID")
                        .withValue(workerId)
                        .build(),
                    EnvVarBuilder()
                        .withName("WORKER_PAYLOAD")
                        .withValue(payload)
                        .build()
                )
                .endContainer()
                .withRestartPolicy("OnFailure")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build()
        ).create()

        return Job(job.metadata.name, Job.State.RUNNING)
    }

    fun deleteJob(id: String) {
        kubernetesClient.batch().v1().jobs().inNamespace("typestream").withName(id).delete()
    }

    fun jobOutput(id: String) = flow {
        val logWatch = kubernetesClient.pods().inNamespace("typestream").withName(id).watchLog()

        val reader = BufferedReader(InputStreamReader(logWatch.output))
        while (true) {
            val line = reader.readLine() ?: break
            emit(line)
        }
    }.flowOn(Dispatchers.IO)
}
