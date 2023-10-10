package io.typestream.k8s

import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import io.github.oshai.kotlinlogging.KotlinLogging

data class Job(val id: String, val state: State) {
    enum class State {
        RUNNING,
        STOPPED,
        UNKNOWN,
    }


}
