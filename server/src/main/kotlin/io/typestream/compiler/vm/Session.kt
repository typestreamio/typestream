package io.typestream.compiler.vm

import java.util.UUID

class Session {
    private val store = mutableMapOf<String, String>()
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

    override fun toString() = store.toString()

    fun toList() = store.toList()
}
