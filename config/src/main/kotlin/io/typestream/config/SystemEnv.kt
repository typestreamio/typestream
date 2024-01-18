package io.typestream.config

object SystemEnv {
    operator fun get(key: String): String? = System.getenv(key)
}
