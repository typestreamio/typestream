package io.typestream.compiler.vm

import io.typestream.compiler.Program
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler

data class Session(val fileSystem: FileSystem, val scheduler: Scheduler, val env: Env) : Cloneable {
    public override fun clone() = Session(fileSystem, scheduler, env.clone())
    val runningPrograms = mutableListOf<Program>()
}
