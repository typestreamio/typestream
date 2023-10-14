package io.typestream.filesystem

abstract class Inode(val name: String) {
    private var parent: Inode? = null
    protected val children: MutableSet<Inode> = mutableSetOf()

    abstract fun stat(): String
    abstract suspend fun watch()
    abstract fun refresh()

    fun findInode(path: String): Inode? {
        var current = this
        val parts = path.split("/").filter(String::isNotBlank).toMutableList()
        parts.add(0, "/")
        var idx = 0

        while (idx < parts.size - 1 && parts[idx] == current.name) {
            current = current.children.find { it.name == parts[idx + 1] } ?: return null
            idx++
        }

        return if (current.name == parts[idx]) current else null
    }

    override fun toString() = print()

    private fun print(inode: Inode = this, indent: Int = 1): String = buildString {
        append(inode.name)
        append("\n")
        for (child in inode.children) {
            append("\t".repeat(indent))
            append(print(child, indent + 1))
        }
    }

    fun add(child: Inode) {
        child.parent = this
        children.add(child)
    }

    fun replaceAll(newChildren: List<Inode>) {
        children.clear()
        newChildren.forEach(::add)
    }

    fun children() = children.toSet()

    fun path() = buildString {
        if (name == "/") {
            append("/")
            return@buildString
        }
        var currentPath: Inode? = this@Inode

        while (currentPath?.parent != null) {
            insert(0, "/${currentPath.name}")
            currentPath = currentPath.parent
        }
    }
}
