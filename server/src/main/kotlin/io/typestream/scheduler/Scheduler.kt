package io.typestream.scheduler

import io.typestream.k8s.K8sClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.Collections

class Scheduler(private val k8sMode: Boolean, private val dispatcher: CoroutineDispatcher) : Closeable {
    private val jobs: Channel<Job> = Channel()
    private val runningJobs = Collections.synchronizedCollection(mutableSetOf<Job>())

    suspend fun start() = coroutineScope {
        if (k8sMode) {
            launch {
                K8sClient().use {
                    it.getJobs().forEach { job ->
                        runningJobs.add(K8sJob(job.id))
                    }
                }
            }
            launch {
                val k8sClient = K8sClient()
                k8sClient.watchJobs().collect { job ->
                    runningJobs.add(K8sJob(job.id))
                }
                k8sClient.close()
            }
        }
        for (job in jobs) {
            runningJobs.add(job)

            launch(dispatcher) {
                job.start()
            }
        }
    }

    suspend fun schedule(job: Job) {
        jobs.send(job)
    }

    fun jobOutput(id: String) = findJob(id).output()

    private fun findJob(id: String) = runningJobs.find { it.id == id } ?: error("job $id is not running")

    fun kill(id: String) {
        val job = findJob(id)
        job.stop()
        job.remove()
        runningJobs.remove(job)
    }

    fun ps() = runningJobs.map(Job::displayName).toList()

    override fun close() {
        runningJobs.filterNot { it is K8sJob }.forEach { job ->
            job.stop()
            job.remove()
        }

        jobs.close()
    }
}
