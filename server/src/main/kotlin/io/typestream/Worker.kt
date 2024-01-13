package io.typestream

import io.github.oshai.kotlinlogging.KotlinLogging
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Vm
import io.typestream.config.Config
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class Worker(val config: Config) {
    private val logger = KotlinLogging.logger {}
    fun run() = runBlocking {
        val id = System.getenv("WORKER_ID") ?: error("WORKER_ID env var is not set")
        val payload = System.getenv("WORKER_PAYLOAD") ?: error("WORKER_PAYLOAD env var is not set")

        val dispatcher = Dispatchers.IO

        logger.info { "starting filesystem" }
        val fileSystem = FileSystem(config.sources, dispatcher)

        fileSystem.refresh()

        logger.info { "file system is ready" }

        val vm = Vm(fileSystem, Scheduler(true, dispatcher))

        logger.info { "executing program $id" }
        vm.exec(payload, Env())
    }
}
