package io.typestream.testing.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.NewTopic
import java.util.Properties
import java.util.concurrent.TimeUnit

class AdminClientWrapper(private val bootstrapServers: String) {
    private val adminClient = AdminClient.create(adminConfig())

    fun createTopics(vararg topics: String) {
        val newTopics = topics.map { e: String -> NewTopic(e, 1, 1.toShort()) }
        adminClient.createTopics(newTopics).all().get(30, TimeUnit.SECONDS)
    }

    fun listTopics() = adminClient.listTopics().names().get(30, TimeUnit.SECONDS).toList()

    private fun adminConfig(): Properties {
        val adminProps = Properties()
        adminProps[BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        return adminProps
    }
}
