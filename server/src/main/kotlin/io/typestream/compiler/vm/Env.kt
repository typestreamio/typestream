package io.typestream.compiler.vm

import io.typestream.compiler.types.Value
import io.typestream.config.Config
import kotlinx.serialization.Serializable
import java.util.UUID

class Env(private val config: Config) : Cloneable {
    private val store = mutableMapOf<String, String>()
    private val variables = mutableMapOf<String, Value>()
    private val history = mutableListOf<String>()

    init {
        store["TYPESTREAM_VERSION"] = config.versionInfo.version
        store["TYPESTREAM_COMMIT_HASH"] = config.versionInfo.commitHash
        store["TYPESTREAM_CONFIG_PATH"] = config.configPath
    }

    var pwd: String
        get() = store["PWD"] ?: "/"
        set(value) {
            store["PWD"] = value
        }
    var id: String
        get() = store["SESSION_ID"] ?: error("session id not set")
        set(value) {
            store["SESSION_ID"] = value
        }

    init {
        id = UUID.randomUUID().toString()
        pwd = "/"
    }

    fun defineVariable(name: String, value: Value) {
        variables[name] = value
    }

    fun getVariable(name: String) = variables[name] ?: error("undefined variable $name")

    override fun toString() = store.toString()

    fun toList() = store.toList()

    public override fun clone() = Env(config).also {
        it.store.putAll(store)
        it.variables.putAll(variables)
    }

    fun addHistoryEntry(entry: String) {
        if (entry.startsWith("history")) {
            return
        }
        history.add(entry)
    }

    fun history() = history.toList()
}
