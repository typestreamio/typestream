package io.typestream.filesystem

class Random(name: String, val valueType: String) : Inode(name) {
    override fun stat(): String {
        TODO("Not yet implemented")
    }

    override suspend fun watch() {}

    override fun refresh() {}
}
