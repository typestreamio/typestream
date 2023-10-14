package io.typestream.filesystem.kafka

import io.typestream.filesystem.Inode


class Broker(name: String) : Inode(name) {
    override fun stat() = TODO("broker stat")

    override suspend fun watch() {}

    override fun refresh() {}
}
