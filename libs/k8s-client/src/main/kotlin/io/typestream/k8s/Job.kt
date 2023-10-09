package io.typestream.k8s

data class Job(val id: String, val state: State) {
    enum class State {
        RUNNING,
        STOPPED,
        UNKNOWN,
    }
}
