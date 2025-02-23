package io.typestream.k8s

import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import io.fabric8.kubernetes.api.model.batch.v1.Job as BatchV1Job

class K8sClient : Closeable {
    private val kubernetesClient = KubernetesClientBuilder()
        .withConfig(
            ConfigBuilder()
                .withConnectionTimeout(100)
                .withRequestTimeout(100)
                .build()
        ).build()

    fun getServerConfig(): InputStream? = kubernetesClient.configMaps()
        .inNamespace("typestream")
        .withName("server-config")
        .get().data["typestream.toml"]?.byteInputStream()

    override fun close() {
        kubernetesClient.close()
    }

    private fun jobQuery() = kubernetesClient.batch().v1().jobs()
        .inNamespace("typestream")
        .withLabel("app.kubernetes.io/name", "worker")

    fun getJobs() = jobQuery().list().items.map { job ->
        Job(
            job.metadata.name,
            when (job.status.active) {
                1 -> Job.State.RUNNING
                0 -> Job.State.STOPPED
                else -> Job.State.UNKNOWN
            }
        )
    }

    fun createWorkerJob(typestreamVersion: String, workerId: String, payload: String): Job {
        val image = if (typestreamVersion == "beta") {
            "localhost:5001/typestream/server:beta"
        } else {
            "typestream/server:$typestreamVersion"
        }

        val job = kubernetesClient.batch().v1().jobs().inNamespace("typestream").resource(
            JobBuilder()
                .withNewMetadata()
                .withName("worker-$workerId")
                .withLabels<String, String>(
                    mapOf(
                        "app.kubernetes.io/name" to "worker",
                        "app.kubernetes.io/instance" to "worker-$workerId",
                        "app.kubernetes.io/version" to typestreamVersion,
                        "app.kubernetes.io/component" to "worker",
                        "app.kubernetes.io/part-of" to "typestream",
                        "app.kubernetes.io/managed-by" to "typestream-server",
                    ),
                )
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

    fun watchJobs() = callbackFlow {
        jobQuery().watch(
            object : Watcher<BatchV1Job> {
                override fun eventReceived(action: Watcher.Action?, resource: BatchV1Job?) {
                    if (resource != null && action == Watcher.Action.ADDED) {
                        trySend(Job(resource.metadata.name, Job.State.RUNNING))
                    }
                }

                override fun onClose(cause: WatcherException?) {}
            })

        awaitClose {
            channel.close()
        }
    }.flowOn(Dispatchers.IO)
}
