package io.typestream.filesystem.kafka

import io.typestream.filesystem.Inode
import io.typestream.kafka.KafkaAdminClient


class Topic(name: String, private val kafkaAdminClient: KafkaAdminClient) : Inode(name) {
    override fun stat() = kafkaAdminClient.topicInfo(name)

    override suspend fun watch() {}

    override fun refresh() {}
}
