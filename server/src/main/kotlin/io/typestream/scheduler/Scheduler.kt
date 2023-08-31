package io.typestream.scheduler

import io.typestream.config.SourcesConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.Closeable

class Scheduler(
    val sourcesConfig: SourcesConfig,
    private val jobs: Channel<Job> = Channel(),
    private val dispatcher: CoroutineDispatcher,
) : Closeable {
    private val runningJobs = mutableSetOf<Job>()
    private val jobsStdout = mutableMapOf<Job, Flow<String>>()

    suspend fun start() = coroutineScope {
        for (job in jobs) {
            runningJobs.add(job)

            if (job.program.hasMoreOutput()) {
                jobsStdout[job] = job.startForeground()
            } else {
                launch(dispatcher) {
                    job.startBackground()
                }
            }
        }
    }

    suspend fun schedule(job: Job) {
        jobs.send(job)
    }

    fun jobOutput(id: String) = jobsStdout[findJob(id)] ?: error("job $id does not have output")

    private fun findJob(id: String) = runningJobs.find { it.program.id == id } ?: error("job $id is not running")

    fun kill(id: String) {
        val job = findJob(id)
        job.stop()
        job.remove()
        jobsStdout.remove(job)
        runningJobs.remove(job)
    }

    fun killFg() {
        val job = runningJobs.find { it.program.hasMoreOutput() } ?: error("no foreground job running")
        job.stop()
        job.remove()
        jobsStdout.remove(job)
        runningJobs.remove(job)
    }

    fun ps() = runningJobs.toList()

    override fun close() {
        runningJobs.forEach(Job::stop)
        runningJobs.forEach(Job::remove)
        jobs.close()
    }
}
