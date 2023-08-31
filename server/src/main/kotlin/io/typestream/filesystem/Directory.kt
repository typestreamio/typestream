package io.typestream.filesystem

open class Directory(name: String) : Inode(name) {
    override fun stat() = buildString {
        appendLine("File: $name")

        appendLine("children: ${children.size}")
    }

    override suspend fun watch() {
        children.forEach { it.watch() }
    }
}
