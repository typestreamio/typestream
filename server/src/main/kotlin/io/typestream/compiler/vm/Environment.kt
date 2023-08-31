package io.typestream.compiler.vm

import io.typestream.compiler.types.Value
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler


data class Environment(val fileSystem: FileSystem, val scheduler: Scheduler, val session: Session) : Cloneable {
    private val variables = mutableMapOf<String, Value>()

    fun defineVariable(name: String, value: Value) {
        variables[name] = value
    }

    fun getVariable(name: String) = variables[name] ?: error("undefined variable $name")

    override fun toString() = buildString {
        appendLine("variables: $variables")
    }

    fun toList() = variables.toList()

    public override fun clone(): Environment {
        val env = Environment(fileSystem, scheduler, session)
        env.variables.putAll(variables)
        return env
    }
}
