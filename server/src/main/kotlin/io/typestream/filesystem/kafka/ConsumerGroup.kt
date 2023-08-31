package io.typestream.filesystem.kafka

import io.typestream.filesystem.Inode


class ConsumerGroup(name: String) : Inode(name) {
    override fun stat() = TODO("consumer group stat")

    override suspend fun watch() {}
}
